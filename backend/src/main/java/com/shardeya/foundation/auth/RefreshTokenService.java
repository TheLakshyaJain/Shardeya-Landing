package com.shardeya.foundation.auth;

import com.shardeya.platform.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens, stored server-side in Postgres (not literally in
 * Redis, despite 00-ARCHITECTURE.md §1.3's stack table saying "opaque refresh
 * (Redis, 30d)") — see CLAUDE.md "Milestone 1" notes for why: the
 * family/reuse-detection model in 01-DATA-MODEL.md's refresh_token table is
 * relational by nature and needs to survive a Redis restart, an important
 * durability property for security-critical revocation state.
 *
 * <p>Rotation gives each new token a fresh TTL of the same duration
 * established at the original login (30d remember-me / 12h default) rather
 * than inheriting a fixed absolute session end — a sliding session that stays
 * alive indefinitely under active use, which is the interpretation CLAUDE.md's
 * M-01 spec leaves as a judgment call.
 */
@Service
public class RefreshTokenService {

    public static final Duration REMEMBER_ME_TTL = Duration.ofDays(30);
    public static final Duration DEFAULT_TTL = Duration.ofHours(12);

    private final RefreshTokenRepository repository;
    private final TransactionTemplate requiresNewTransaction;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    public record IssueResult(String rawToken, Instant expiresAt, UUID userId) {
    }

    @Transactional
    public IssueResult issue(UUID userId, boolean rememberMe, String userAgent, String ip) {
        return issueForFamily(userId, UUID.randomUUID(), rememberMe, userAgent, ip);
    }

    @Transactional
    public IssueResult rotate(String rawToken, String userAgent, String ip) {
        String hash = hash(rawToken);
        RefreshToken existing = repository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("error.auth.refreshInvalid"));

        if (existing.getRevokedAt() != null) {
            // Someone presented a token that was already rotated away — classic
            // stolen-refresh-token signature. Contain the damage: kill the whole
            // family so both the legitimate holder and the attacker are forced
            // to re-authenticate.
            //
            // This MUST commit in its own transaction. Throwing
            // UnauthorizedException right after would otherwise roll back this
            // method's @Transactional and silently undo the very revocation
            // meant to contain the breach — caught by
            // RefreshTokenServiceTest: the legitimate second token stayed
            // usable after a reuse attempt on the first, because the revoke
            // never actually reached the database. (A `@Transactional(REQUIRES_NEW)`
            // private/protected method here would NOT have fixed it either —
            // self-invocation within the same class bypasses Spring's AOP
            // proxy entirely, silently ignoring the annotation. Explicit
            // TransactionTemplate sidesteps that trap.)
            requiresNewTransaction.executeWithoutResult(
                    status -> repository.revokeFamily(existing.getFamilyId(), Instant.now()));
            throw new UnauthorizedException("error.auth.refreshReused");
        }
        if (!existing.isActive()) {
            throw new UnauthorizedException("error.auth.refreshExpired");
        }

        boolean rememberMe = Duration.between(Instant.now(), existing.getExpiresAt())
                .compareTo(DEFAULT_TTL) > 0;
        IssueResult next = issueForFamily(existing.getUserId(), existing.getFamilyId(), rememberMe, userAgent, ip);

        existing.revoke();
        RefreshToken saved = repository.findByTokenHash(hash(next.rawToken())).orElseThrow();
        existing.setReplacedBy(saved.getId());

        return next;
    }

    @Transactional
    public void revokeFamilyOf(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.findByUserIdAndRevokedAtIsNull(userId)
                .forEach(RefreshToken::revoke);
    }

    private IssueResult issueForFamily(UUID userId, UUID familyId, boolean rememberMe, String userAgent, String ip) {
        String rawToken = generateToken();
        Instant expiresAt = Instant.now().plus(rememberMe ? REMEMBER_ME_TTL : DEFAULT_TTL);
        RefreshToken token = new RefreshToken(
                UUID.randomUUID(), userId, hash(rawToken), familyId, expiresAt, userAgent, ip);
        repository.save(token);
        return new IssueResult(rawToken, expiresAt, userId);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
