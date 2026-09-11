package com.shardeya.platform;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Self-issued JWT access tokens (00-ARCHITECTURE.md §1.3: "Self-issued JWT
 * access (15 min) + opaque refresh"). Refresh tokens are NOT JWTs — see
 * {@code RefreshTokenService} — this class only ever handles the short-lived
 * access token.
 */
@Component
public class JwtService {

    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final SecretKey key;

    public JwtService(@Value("${shardeya.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public record AccessTokenClaims(
            UUID userId, UUID orgId, String orgType, String roleCode, List<String> permissions, int tokenVersion,
            boolean allProjects, List<UUID> projectScope) {

        /** Convenience for the common ALL-access case (owner, most system paths). */
        public AccessTokenClaims(UUID userId, UUID orgId, String orgType, String roleCode, List<String> permissions,
                                  int tokenVersion) {
            this(userId, orgId, orgType, roleCode, permissions, tokenVersion, true, List.of());
        }
    }

    public String issueAccessToken(AccessTokenClaims claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(claims.userId().toString())
                .claim("org", claims.orgId().toString())
                .claim("orgType", claims.orgType())
                .claim("role", claims.roleCode())
                .claim("perms", claims.permissions())
                .claim("tv", claims.tokenVersion())
                .claim("allProj", claims.allProjects())
                .claim("projScope", claims.projectScope().stream().map(UUID::toString).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TOKEN_TTL)))
                .signWith(key)
                .compact();
    }

    /**
     * @throws UnauthorizedException if the token is missing, malformed, expired,
     *                                or has an invalid signature — never leaks
     *                                which of those it was, same as login's
     *                                generic-error rule (CLAUDE.md pitfall #14
     *                                by extension: don't hand an attacker a
     *                                token-forgery oracle).
     */
    public AccessTokenClaims parseAndValidate(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            @SuppressWarnings("unchecked")
            List<String> permissions = (List<String>) claims.get("perms", List.class);
            @SuppressWarnings("unchecked")
            List<String> projScopeStrings = (List<String>) claims.get("projScope", List.class);
            List<UUID> projectScope = projScopeStrings == null ? List.of()
                    : projScopeStrings.stream().map(UUID::fromString).toList();
            Boolean allProjects = claims.get("allProj", Boolean.class);
            return new AccessTokenClaims(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get("org", String.class)),
                    claims.get("orgType", String.class),
                    claims.get("role", String.class),
                    permissions,
                    claims.get("tv", Integer.class),
                    allProjects == null || allProjects,
                    projectScope);
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("error.auth.tokenExpired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("error.auth.tokenInvalid");
        }
    }
}
