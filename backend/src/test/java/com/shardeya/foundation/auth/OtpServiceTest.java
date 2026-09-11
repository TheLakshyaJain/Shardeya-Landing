package com.shardeya.foundation.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.TooManyRequestsException;
import com.shardeya.support.AbstractIntegrationTest;
import com.shardeya.support.MailHogReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Channel-agnostic since the Email OTP fix — every test here exercises the
 * SMS channel (the SMS stub log, unaffected) except
 * {@link #emailChannelActuallyDeliversAReadableCodeViaRealSmtp}, which
 * proves the EMAIL channel end to end against a real Testcontainers-managed
 * MailHog (see AbstractIntegrationTest's own javadoc for why that container
 * exists at all — this is precisely the test it exists for).
 */
class OtpServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OtpService otpService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ObjectMapper objectMapper;

    private String uniqueMobile() {
        // Real 10-digit-shaped mobile so it doesn't collide with the rate-limit
        // counters other tests in this class use.
        return "9" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
    }

    private String uniqueEmail() {
        return "otp-test-" + UUID.randomUUID() + "@example.com";
    }

    private String lastCodeFor(String mobile) throws IOException {
        Path logFile = Path.of("/tmp/shardeya-otp-outbox.log");
        String content = Files.readString(logFile);
        Matcher matcher = Pattern.compile("mobile=" + mobile + " \\| code=(\\d{6})").matcher(content);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        assertThat(last).as("OTP code logged for %s", mobile).isNotNull();
        return last;
    }

    @Test
    void createThenVerifyWithCorrectCodeSucceeds() throws IOException {
        String mobile = uniqueMobile();
        OtpService.ChallengeResult challenge = otpService.create(
                OtpService.PURPOSE_SIGNUP, OtpService.Channel.SMS, mobile, "1.2.3.4", "{\"fullName\":\"Test\"}");

        String code = lastCodeFor(mobile);
        OtpService.VerifyResult result = otpService.verify(challenge.challengeId(), code);

        assertThat(result.purpose()).isEqualTo(OtpService.PURPOSE_SIGNUP);
        assertThat(result.recipient()).isEqualTo(mobile);
        assertThat(result.payloadJson()).isEqualTo("{\"fullName\":\"Test\"}");
    }

    // The actual shape signup/login-with-OTP use in production now --
    // verifies the full real-SMTP round trip, not just that create()/verify()
    // don't throw.
    @Test
    void emailChannelActuallyDeliversAReadableCodeViaRealSmtp() throws Exception {
        String email = uniqueEmail();
        OtpService.ChallengeResult challenge = otpService.create(
                OtpService.PURPOSE_LOGIN, OtpService.Channel.EMAIL, email, "1.2.3.4", "payload-123");
        assertThat(challenge.maskedRecipient()).endsWith("@example.com");
        assertThat(challenge.maskedRecipient()).doesNotContain(email); // never the full, unmasked address

        String code = MailHogReader.lastOtpCodeFor(mailhogApiBaseUrl(), email);
        OtpService.VerifyResult result = otpService.verify(challenge.challengeId(), code);

        assertThat(result.purpose()).isEqualTo(OtpService.PURPOSE_LOGIN);
        assertThat(result.recipient()).isEqualTo(email);
        assertThat(result.payloadJson()).isEqualTo("payload-123");
    }

    @Test
    void verifyConsumesTheChallengeSoItCannotBeReplayed() throws IOException {
        String mobile = uniqueMobile();
        OtpService.ChallengeResult challenge = otpService.create(OtpService.PURPOSE_LOGIN, OtpService.Channel.SMS, mobile, "1.2.3.4", null);
        String code = lastCodeFor(mobile);

        otpService.verify(challenge.challengeId(), code);

        assertThatThrownBy(() -> otpService.verify(challenge.challengeId(), code))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void fiveWrongAttemptsInvalidatesTheChallenge() {
        String mobile = uniqueMobile();
        OtpService.ChallengeResult challenge = otpService.create(OtpService.PURPOSE_LOGIN, OtpService.Channel.SMS, mobile, "1.2.3.4", null);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> otpService.verify(challenge.challengeId(), "000000"))
                    .isInstanceOf(BadRequestException.class);
        }
        // 5th wrong attempt trips the max and deletes the challenge outright.
        assertThatThrownBy(() -> otpService.verify(challenge.challengeId(), "000000"))
                .isInstanceOf(BadRequestException.class);

        // Even the *correct* code no longer works — challenge is gone.
        assertThatThrownBy(() -> otpService.verify(challenge.challengeId(), "111111"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void resendBeforeCooldownIsRejected() {
        String mobile = uniqueMobile();
        OtpService.ChallengeResult challenge = otpService.create(OtpService.PURPOSE_LOGIN, OtpService.Channel.SMS, mobile, "1.2.3.4", null);

        assertThatThrownBy(() -> otpService.resend(challenge.challengeId(), "1.2.3.4"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void resendAfterCooldownElapsedSucceeds() throws IOException {
        String mobile = uniqueMobile();
        OtpService.ChallengeResult challenge = otpService.create(OtpService.PURPOSE_LOGIN, OtpService.Channel.SMS, mobile, "1.2.3.4", null);

        // Backdate lastResendAt directly in Redis rather than sleeping 60 real
        // seconds — proves the cooldown logic itself, not wall-clock patience.
        String key = "otp:challenge:" + challenge.challengeId();
        String json = redis.opsForValue().get(key);
        OtpChallengeData data = objectMapper.readValue(json, OtpChallengeData.class);
        OtpChallengeData backdated = new OtpChallengeData(
                data.challengeId(), data.purpose(), data.channel(), data.recipient(), data.codeHash(), data.attempts(),
                data.maxAttempts(), data.createdAt(), Instant.now().minus(120, ChronoUnit.SECONDS),
                data.resendCount(), data.payloadJson());
        Long ttl = redis.getExpire(key);
        redis.opsForValue().set(key, objectMapper.writeValueAsString(backdated), ttl == null ? 300 : ttl, TimeUnit.SECONDS);

        OtpService.ChallengeResult resent = otpService.resend(challenge.challengeId(), "5.6.7.8");
        assertThat(resent.challengeId()).isEqualTo(challenge.challengeId());

        String newCode = lastCodeFor(mobile);
        OtpService.VerifyResult result = otpService.verify(challenge.challengeId(), newCode);
        assertThat(result.recipient()).isEqualTo(mobile);
    }

    @Test
    void fourthOtpRequestInTheSameHourIsRateLimited() {
        String mobile = uniqueMobile();
        otpService.create(OtpService.PURPOSE_LOGIN, OtpService.Channel.SMS, mobile, "9.9.9.9", null);
        otpService.create(OtpService.PURPOSE_LOGIN, OtpService.Channel.SMS, mobile, "9.9.9.9", null);
        otpService.create(OtpService.PURPOSE_LOGIN, OtpService.Channel.SMS, mobile, "9.9.9.9", null);

        assertThatThrownBy(() -> otpService.create(OtpService.PURPOSE_LOGIN, OtpService.Channel.SMS, mobile, "9.9.9.9", null))
                .isInstanceOf(TooManyRequestsException.class);
    }
}
