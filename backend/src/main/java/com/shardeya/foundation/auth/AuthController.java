package com.shardeya.foundation.auth;

import com.shardeya.foundation.auth.dto.AuthTokensResponse;
import com.shardeya.foundation.auth.dto.ForgotPasswordRequest;
import com.shardeya.foundation.auth.dto.LoginOtpRequestRequest;
import com.shardeya.foundation.auth.dto.LoginRequest;
import com.shardeya.foundation.auth.dto.OtpResendRequest;
import com.shardeya.foundation.auth.dto.OtpVerifyRequest;
import com.shardeya.foundation.auth.dto.ResetPasswordRequest;
import com.shardeya.foundation.auth.dto.SignupRequest;
import com.shardeya.foundation.auth.dto.SignupResponse;
import com.shardeya.platform.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/**
 * The refresh token never appears in a JSON body or response field anywhere
 * in this controller (CLAUDE.md rule #15 / the "move refresh token to an
 * httpOnly cookie" fix) — it only ever travels as the {@code refresh_token}
 * Set-Cookie/Cookie header, via {@link #setRefreshCookie}/{@link #clearRefreshCookie}
 * and {@code @CookieValue}. See CLAUDE.md's own writeup for the SameSite
 * choice and why the refresh response now carries real user/org (both are
 * new as of this fix).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    // Scoped to the auth path only -- the browser never needs to send this
    // cookie on any other request, and narrowing where a sensitive cookie is
    // sent is cheap, real defense in depth.
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.signup(request, ip(http)));
    }

    @PostMapping("/otp/verify")
    public AuthTokensResponse verifySignupOtp(@Valid @RequestBody OtpVerifyRequest request,
                                               @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                               HttpServletRequest http, HttpServletResponse response) {
        AuthService.TokenIssueResult result = authService.verifySignupOtp(request, userAgent, ip(http));
        setRefreshCookie(response, result.refreshToken(), result.refreshExpiresAt());
        return result.body();
    }

    @PostMapping("/otp/resend")
    public ResponseEntity<SignupResponse> resendOtp(@Valid @RequestBody OtpResendRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.resendOtp(request, ip(http)));
    }

    @PostMapping("/login")
    public AuthTokensResponse login(@Valid @RequestBody LoginRequest request,
                                     @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                     HttpServletRequest http, HttpServletResponse response) {
        AuthService.TokenIssueResult result = authService.login(request, userAgent, ip(http));
        setRefreshCookie(response, result.refreshToken(), result.refreshExpiresAt());
        return result.body();
    }

    @PostMapping("/login/otp/request")
    public ResponseEntity<SignupResponse> requestLoginOtp(@Valid @RequestBody LoginOtpRequestRequest request,
                                                            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.requestLoginOtp(request, ip(http)));
    }

    @PostMapping("/login/otp/verify")
    public AuthTokensResponse verifyLoginOtp(@Valid @RequestBody OtpVerifyRequest request,
                                              @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                              HttpServletRequest http, HttpServletResponse response) {
        AuthService.TokenIssueResult result = authService.verifyLoginOtp(request, userAgent, ip(http));
        setRefreshCookie(response, result.refreshToken(), result.refreshExpiresAt());
        return result.body();
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<SignupResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.forgotPassword(request, ip(http)));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/accept-invite")
    public ResponseEntity<Void> acceptInvite(@Valid @RequestBody com.shardeya.foundation.auth.dto.AcceptInviteRequest request) {
        authService.acceptInvite(request);
        return ResponseEntity.ok().build();
    }

    // No @RequestBody at all — the refresh token comes exclusively from the
    // httpOnly cookie the browser attaches automatically (apiFetch always
    // sends credentials: 'include'). A missing/blank cookie is treated
    // identically to an invalid one: same generic message key, no oracle for
    // "does this browser have a session at all".
    @PostMapping("/refresh")
    public AuthTokensResponse refresh(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
                                       @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                       HttpServletRequest http, HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("error.auth.refreshInvalid");
        }
        AuthService.TokenIssueResult result = authService.refresh(refreshToken, userAgent, ip(http));
        setRefreshCookie(response, result.refreshToken(), result.refreshExpiresAt());
        return result.body();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
                                        HttpServletResponse response) {
        authService.logout(refreshToken);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    private String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    // SameSite=None + Secure: the frontend (Vite dev server) and backend are
    // already different origins even in local dev (different ports), and a
    // real deployment could plausibly put them on genuinely different
    // registrable domains (a separate API subdomain/gateway, a different
    // domain entirely) rather than just different ports on one host —
    // exactly the "worked in dev, silently broke in prod" shape this
    // codebase has already hit twice for CORS (M1's OPTIONS-preflight gap,
    // the Swagger-UI-allowlist gap). SameSite=Lax would keep working today
    // (localhost:5173 and localhost:8080 are same-site — same registrable
    // domain "localhost", different port only — so Lax would still send the
    // cookie), but would silently stop working the moment either origin
    // moves to a genuinely different domain, with no compile-time or
    // dev-environment signal that anything changed. None+Secure is chosen
    // deliberately up front so that never becomes a third occurrence of the
    // same bug class. Secure does NOT require HTTPS for localhost
    // specifically — Chrome/Firefox (this project's e2e/manual-verification
    // browsers) treat http://localhost as a "potentially trustworthy origin"
    // and will both set and return a Secure cookie over plain HTTP there;
    // confirmed live as part of this fix's own verification pass, not
    // assumed.
    private void setRefreshCookie(HttpServletResponse response, String rawToken, Instant expiresAt) {
        long maxAgeSeconds = Math.max(0, Duration.between(Instant.now(), expiresAt).toSeconds());
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
