package com.shardeya.foundation.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardeya.foundation.notification.EmailGateway;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.RateLimiter;
import com.shardeya.platform.TooManyRequestsException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis is the sole store for OTP challenges in M1 (no DB audit table — see
 * CLAUDE.md "Milestone 1" notes). 00-ARCHITECTURE.md §5: "6-digit, Redis TTL
 * 5 min, hashed at rest, 5 verify attempts then invalidate, 60s resend
 * cooldown, rate-limited per recipient." Signup's own payload lives in the
 * same challenge with a longer 15-min TTL (M-01 §7: "the payload lives in
 * Redis under challengeId (TTL 15 min)").
 *
 * <p><b>Channel-agnostic since the "Email OTP" fix</b> — originally
 * SMS-only (every purpose sent to a mobile number). Signup verification and
 * "login with OTP" both moved to email delivery (the SMS adapter is still
 * stubbed — real SMS needs India DLT registration, which is deliberately
 * being avoided for now; the org already has working email on its own
 * domain, which is the practical channel today). {@code PURPOSE_RESET}'s
 * mobile-identifier branch and {@code WhatsAppOptInService} (which
 * inherently needs to verify a real WhatsApp-reachable phone number, not an
 * email) both still use {@link Channel#SMS} explicitly and are completely
 * unaffected — this class was generalized to take an explicit channel per
 * challenge rather than having its two callers duplicate the whole
 * generate/hash/store/rate-limit/verify mechanism a second time.
 * {@link SmsGateway} is left fully in place, not removed — it's the path
 * this app switches back to (or runs alongside) once DLT registration
 * happens.
 */
@Service
public class OtpService {

    public static final String PURPOSE_SIGNUP = "SIGNUP";
    public static final String PURPOSE_LOGIN = "LOGIN";
    public static final String PURPOSE_RESET = "RESET";

    /** Which gateway a given challenge was/will be sent through — stored on the challenge itself so {@link #resend} knows without being told again. */
    public enum Channel { SMS, EMAIL }

    private static final Duration SIGNUP_TTL = Duration.ofMinutes(15);
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_PER_RECIPIENT_PER_HOUR = 3;
    private static final int MAX_PER_IP_PER_DAY = 10;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SmsGateway smsGateway;
    private final EmailGateway emailGateway;
    private final RateLimiter rateLimiter;
    private final SecureRandom random = new SecureRandom();

    public OtpService(StringRedisTemplate redis, ObjectMapper objectMapper, SmsGateway smsGateway,
                       EmailGateway emailGateway, RateLimiter rateLimiter) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.smsGateway = smsGateway;
        this.emailGateway = emailGateway;
        this.rateLimiter = rateLimiter;
    }

    public record ChallengeResult(String challengeId, String maskedRecipient, long resendAfterSeconds) {
    }

    public record VerifyResult(String purpose, String recipient, String payloadJson) {
    }

    public ChallengeResult create(String purpose, Channel channel, String recipient, String ip, String payloadJson) {
        checkRateLimits(recipient, ip);
        String challengeId = UUID.randomUUID().toString();
        String code = generateCode();
        Instant now = Instant.now();
        OtpChallengeData data = new OtpChallengeData(
                challengeId, purpose, channel, recipient, hash(code), 0, MAX_ATTEMPTS, now, now, 1, payloadJson);
        store(challengeId, data, ttlFor(purpose));
        sendCode(channel, recipient, code, purpose);
        return new ChallengeResult(challengeId, mask(channel, recipient), RESEND_COOLDOWN.toSeconds());
    }

    public ChallengeResult resend(String challengeId, String ip) {
        OtpChallengeData data = load(challengeId)
                .orElseThrow(() -> new BadRequestException("challengeId", "OTP_EXPIRED", "error.otp.expired"));

        Duration sinceLast = Duration.between(data.lastResendAt(), Instant.now());
        if (sinceLast.compareTo(RESEND_COOLDOWN) < 0) {
            long retryAfter = RESEND_COOLDOWN.minus(sinceLast).toSeconds();
            throw new TooManyRequestsException("error.otp.resendTooSoon", Map.of("retryAfterSeconds", retryAfter));
        }
        checkRateLimits(data.recipient(), ip);

        String code = generateCode();
        OtpChallengeData updated = data.withCode(hash(code), Instant.now());
        store(challengeId, updated, ttlFor(data.purpose()));
        sendCode(data.channel(), data.recipient(), code, data.purpose());
        return new ChallengeResult(challengeId, mask(data.channel(), data.recipient()), RESEND_COOLDOWN.toSeconds());
    }

    public VerifyResult verify(String challengeId, String code) {
        OtpChallengeData data = load(challengeId)
                .orElseThrow(() -> new BadRequestException("challengeId", "OTP_EXPIRED", "error.otp.expired"));

        if (!hash(code).equals(data.codeHash())) {
            OtpChallengeData updated = data.withAttemptIncremented();
            if (updated.attempts() >= updated.maxAttempts()) {
                delete(challengeId);
                throw new BadRequestException("code", "OTP_MAX_ATTEMPTS", "error.otp.maxAttempts");
            }
            Duration remainingTtl = remainingTtl(challengeId);
            store(challengeId, updated, remainingTtl);
            throw new BadRequestException("code", "OTP_INVALID", "error.otp.invalid");
        }

        delete(challengeId);
        return new VerifyResult(data.purpose(), data.recipient(), data.payloadJson());
    }

    private void sendCode(Channel channel, String recipient, String code, String purpose) {
        if (channel == Channel.EMAIL) {
            emailGateway.send(recipient, emailSubjectFor(purpose), emailBodyFor(purpose, code));
        } else {
            smsGateway.sendOtp(recipient, code, purpose);
        }
    }

    private String emailSubjectFor(String purpose) {
        return switch (purpose) {
            case PURPOSE_SIGNUP -> "Verify your Shardeya account";
            case PURPOSE_LOGIN -> "Your Shardeya login code";
            default -> "Your Shardeya verification code";
        };
    }

    private String emailBodyFor(String purpose, String code) {
        long minutes = ttlFor(purpose).toMinutes();
        return "Your verification code is: " + code
                + "\nThis code expires in " + minutes + " minutes and can only be used once."
                + "\nIf you didn't request this, you can safely ignore this email.";
    }

    private void checkRateLimits(String recipient, String ip) {
        boolean recipientOk = rateLimiter.tryConsume("otp:rate:recipient:" + recipient, MAX_PER_RECIPIENT_PER_HOUR, Duration.ofHours(1));
        if (!recipientOk) {
            throw new TooManyRequestsException("error.otp.rateLimitedRecipient", Map.of());
        }
        if (ip != null) {
            boolean ipOk = rateLimiter.tryConsume("otp:rate:ip:" + ip, MAX_PER_IP_PER_DAY, Duration.ofDays(1));
            if (!ipOk) {
                throw new TooManyRequestsException("error.otp.rateLimitedIp", Map.of());
            }
        }
    }

    private Duration ttlFor(String purpose) {
        return PURPOSE_SIGNUP.equals(purpose) ? SIGNUP_TTL : DEFAULT_TTL;
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String mask(Channel channel, String recipient) {
        return channel == Channel.EMAIL ? maskEmail(recipient) : maskMobile(recipient);
    }

    private String maskMobile(String mobile) {
        if (mobile.length() <= 2) {
            return mobile;
        }
        return "X".repeat(mobile.length() - 2) + mobile.substring(mobile.length() - 2);
    }

    // Keeps a little of the local part for recognisability (matches
    // maskMobile's "show a bit, hide the rest" spirit) while never
    // revealing the whole address -- "mridul@example.com" -> "mr***@example.com".
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + domain;
    }

    private void store(String challengeId, OtpChallengeData data, Duration ttl) {
        try {
            redis.opsForValue().set(redisKey(challengeId), objectMapper.writeValueAsString(data), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store OTP challenge", e);
        }
    }

    private Optional<OtpChallengeData> load(String challengeId) {
        String json = redis.opsForValue().get(redisKey(challengeId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, OtpChallengeData.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Duration remainingTtl(String challengeId) {
        Long seconds = redis.getExpire(redisKey(challengeId));
        return seconds == null || seconds <= 0 ? DEFAULT_TTL : Duration.ofSeconds(seconds);
    }

    private void delete(String challengeId) {
        redis.delete(redisKey(challengeId));
    }

    private String redisKey(String challengeId) {
        return "otp:challenge:" + challengeId;
    }
}
