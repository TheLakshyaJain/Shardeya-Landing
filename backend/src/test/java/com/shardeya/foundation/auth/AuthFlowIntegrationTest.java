package com.shardeya.foundation.auth;

import com.shardeya.foundation.auth.dto.AuthTokensResponse;
import com.shardeya.foundation.auth.dto.ForgotPasswordRequest;
import com.shardeya.foundation.auth.dto.LoginOtpRequestRequest;
import com.shardeya.foundation.auth.dto.LoginRequest;
import com.shardeya.foundation.auth.dto.MeResponse;
import com.shardeya.foundation.auth.dto.OtpResendRequest;
import com.shardeya.foundation.auth.dto.OtpVerifyRequest;
import com.shardeya.foundation.auth.dto.ResetPasswordRequest;
import com.shardeya.foundation.auth.dto.SignupRequest;
import com.shardeya.foundation.auth.dto.SignupResponse;
import com.shardeya.platform.EmailPayload;
import com.shardeya.platform.OutboxEventRepository;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.support.AbstractIntegrationTest;
import com.shardeya.support.MailHogReader;
import com.shardeya.support.OtpStubReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Drives the whole M-01 flow through real HTTP calls (not the repository or
 * service layer directly) — signup, OTP, login, /me, tenant isolation,
 * forgot/reset password, refresh rotation and reuse detection, logout. Two
 * full orgs (a builder and a broker) so cross-tenant checks are against real
 * data, not mocks.
 *
 * <p><b>Rewritten for the httpOnly-cookie refresh-token fix.</b> The refresh
 * token no longer appears anywhere in a JSON response body — {@code
 * AuthTokensResponse} dropped the field entirely — so every test that used
 * to thread {@code tokens.refreshToken()} straight into a follow-up request
 * body now extracts the cookie from the {@code Set-Cookie} response header
 * via {@link #extractRefreshCookie} and replays it as a {@code Cookie}
 * request header via {@link #withCookie}. {@code TestRestTemplate} does not
 * manage cookies across separate calls the way a real browser would, so this
 * manual extract/replay is what stands in for "the browser sends it back
 * automatically."
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @LocalServerPort
    private int port;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // Every test in this class hits /auth/signup from the same loopback IP,
    // against the same Testcontainers Redis instance shared for the whole
    // class (see AbstractIntegrationTest's singleton-container javadoc) — so
    // OtpService's real MAX_PER_IP_PER_DAY=10 rate limit (working exactly as
    // designed) trips partway through the class's own tests, not because
    // of anything a real client would ever hit. Flushing between tests keeps
    // each test's rate-limit state isolated the way a real day boundary
    // would in production, without loosening the actual limit.
    @BeforeEach
    void resetRateLimitState() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1" + path;
    }

    private String uniqueMobile() {
        return "9" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
    }

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    private ResponseEntity<AuthTokensResponse> signupAndVerify(Organization.Type role, String mobile, String email, String password)
            throws Exception {
        SignupRequest signup = new SignupRequest(
                "Test " + role, mobile, email, password, password, role, "Pune", true);
        ResponseEntity<SignupResponse> signupResp = restTemplate.postForEntity(url("/auth/signup"), signup, SignupResponse.class);
        assertThat(signupResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Email OTP fix: signup verification is delivered by email now, not
        // SMS -- read the real code back from the Testcontainers-managed
        // MailHog (see AbstractIntegrationTest), not the SMS stub log.
        String code = MailHogReader.lastOtpCodeFor(mailhogApiBaseUrl(), email);
        OtpVerifyRequest verify = new OtpVerifyRequest(signupResp.getBody().challengeId(), code);
        ResponseEntity<AuthTokensResponse> verifyResp = restTemplate.postForEntity(url("/auth/otp/verify"), verify, AuthTokensResponse.class);
        assertThat(verifyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return verifyResp;
    }

    private HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    /** Replays a previously-extracted refresh-token cookie value as a plain {@code Cookie} request header. */
    private HttpEntity<Void> withCookie(String cookieValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, REFRESH_COOKIE_NAME + "=" + cookieValue);
        return new HttpEntity<>(headers);
    }

    private String rawSetCookieHeader(ResponseEntity<?> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).as("response must carry a Set-Cookie header").isNotNull();
        return setCookies.stream()
                .filter(c -> c.startsWith(REFRESH_COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + REFRESH_COOKIE_NAME + " cookie in response: " + setCookies));
    }

    private String extractRefreshCookie(ResponseEntity<?> response) {
        String setCookie = rawSetCookieHeader(response);
        String afterName = setCookie.substring((REFRESH_COOKIE_NAME + "=").length());
        return afterName.split(";", 2)[0];
    }

    private long extractMaxAgeSeconds(ResponseEntity<?> response) {
        String setCookie = rawSetCookieHeader(response);
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, "Max-Age=", 0, "Max-Age=".length())) {
                return Long.parseLong(trimmed.substring("Max-Age=".length()));
            }
        }
        throw new AssertionError("no Max-Age attribute on cookie: " + setCookie);
    }

    @Test
    void builderSignupOtpVerifyAndMeReflectsBuilderPermissions() throws Exception {
        AuthTokensResponse tokens = signupAndVerify(Organization.Type.BUILDER, uniqueMobile(), uniqueEmail(), "Passw0rd1").getBody();

        assertThat(tokens.user().isOwner()).isTrue();
        assertThat(tokens.org().type()).isEqualTo("BUILDER");

        ResponseEntity<MeResponse> me = restTemplate.exchange(url("/me"), HttpMethod.GET,
                bearer(tokens.accessToken()), MeResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().org().type()).isEqualTo("BUILDER");
        // Builder-only permission must be present for BUILDER_ADMIN.
        assertThat(me.getBody().permissions()).contains("PROJECT_CREATE", "PLOT_BULK_UPLOAD");
    }

    @Test
    void brokerSignupExcludesBuilderOnlyPermissions() throws Exception {
        AuthTokensResponse tokens = signupAndVerify(Organization.Type.BROKER, uniqueMobile(), uniqueEmail(), "Passw0rd1").getBody();

        assertThat(tokens.org().type()).isEqualTo("BROKER");

        ResponseEntity<MeResponse> me = restTemplate.exchange(url("/me"), HttpMethod.GET,
                bearer(tokens.accessToken()), MeResponse.class);
        assertThat(me.getBody().permissions()).doesNotContain("PROJECT_CREATE", "BROKER_MANAGE");
        assertThat(me.getBody().permissions()).contains("FINANCIAL_VIEW");
    }

    @Test
    void meNeverLeaksAcrossTenants() throws Exception {
        String mobileA = uniqueMobile();
        String mobileB = uniqueMobile();
        AuthTokensResponse orgA = signupAndVerify(Organization.Type.BUILDER, mobileA, uniqueEmail(), "Passw0rd1").getBody();
        AuthTokensResponse orgB = signupAndVerify(Organization.Type.BROKER, mobileB, uniqueEmail(), "Passw0rd1").getBody();

        MeResponse meA = restTemplate.exchange(url("/me"), HttpMethod.GET,
                bearer(orgA.accessToken()), MeResponse.class).getBody();
        MeResponse meB = restTemplate.exchange(url("/me"), HttpMethod.GET,
                bearer(orgB.accessToken()), MeResponse.class).getBody();

        assertThat(meA.user().mobile()).isEqualTo(mobileA);
        assertThat(meB.user().mobile()).isEqualTo(mobileB);
        assertThat(meA.org().id()).isNotEqualTo(meB.org().id());
        assertThat(meA.user().mobile()).isNotEqualTo(meB.user().mobile());
    }

    @Test
    void loginWithPasswordSucceedsAndWrongPasswordFails() throws Exception {
        String mobile = uniqueMobile();
        String email = uniqueEmail();
        signupAndVerify(Organization.Type.BUILDER, mobile, email, "Passw0rd1");

        ResponseEntity<AuthTokensResponse> ok = restTemplate.postForEntity(
                url("/auth/login"), new LoginRequest(mobile, "Passw0rd1", false), AuthTokensResponse.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().accessToken()).isNotBlank();

        ResponseEntity<String> wrong = restTemplate.postForEntity(
                url("/auth/login"), new LoginRequest(mobile, "WrongPassword1", false), String.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // The cookie shape itself: httpOnly (never readable via JS — CLAUDE.md
    // rule #15), Secure, SameSite=None, scoped to /api/v1/auth only, and
    // never present as a JSON field on the response body at all (a plain
    // compile-time guarantee now that AuthTokensResponse has no such field,
    // but worth asserting the actual wire shape too).
    @Test
    void loginSetsARealHttpOnlySecureRefreshCookieAndNeverReturnsItInTheBody() throws Exception {
        String mobile = uniqueMobile();
        signupAndVerify(Organization.Type.BUILDER, mobile, uniqueEmail(), "Passw0rd1");

        ResponseEntity<AuthTokensResponse> login = restTemplate.postForEntity(
                url("/auth/login"), new LoginRequest(mobile, "Passw0rd1", false), AuthTokensResponse.class);

        String setCookie = rawSetCookieHeader(login);
        assertThat(setCookie).containsIgnoringCase("HttpOnly");
        assertThat(setCookie).containsIgnoringCase("Secure");
        assertThat(setCookie).containsIgnoringCase("SameSite=None");
        assertThat(setCookie).contains("Path=/api/v1/auth");
        assertThat(extractRefreshCookie(login)).isNotBlank();
    }

    // "Remember me" must give the 30-day refresh lifetime and a plain login
    // the 12-hour default — encoded directly as the cookie's own Max-Age, not
    // just as an internal DB expiry nobody outside the server ever observes.
    @Test
    void rememberMeControlsTheRefreshCookiesMaxAge() throws Exception {
        String mobile = uniqueMobile();
        signupAndVerify(Organization.Type.BUILDER, mobile, uniqueEmail(), "Passw0rd1");

        ResponseEntity<AuthTokensResponse> remembered = restTemplate.postForEntity(
                url("/auth/login"), new LoginRequest(mobile, "Passw0rd1", true), AuthTokensResponse.class);
        assertThat(extractMaxAgeSeconds(remembered))
                .isCloseTo(Duration.ofDays(30).toSeconds(), within(60L));

        ResponseEntity<AuthTokensResponse> notRemembered = restTemplate.postForEntity(
                url("/auth/login"), new LoginRequest(mobile, "Passw0rd1", false), AuthTokensResponse.class);
        assertThat(extractMaxAgeSeconds(notRemembered))
                .isCloseTo(Duration.ofHours(12).toSeconds(), within(60L));
    }

    // Email OTP fix: "login with OTP" is delivered by email now. The
    // identifier stays flexible (mobile OR email, like password login) --
    // this test exercises the common case (typing the email directly);
    // requestLoginOtpByMobileIdentifierAlsoWorksWhenTheAccountHasAnEmail
    // below proves the mobile-identifier path resolves to the same account.
    @Test
    void loginWithOtpSucceeds() throws Exception {
        String mobile = uniqueMobile();
        String email = uniqueEmail();
        signupAndVerify(Organization.Type.BUILDER, mobile, email, "Passw0rd1");

        ResponseEntity<SignupResponse> requested = restTemplate.postForEntity(
                url("/auth/login/otp/request"), new LoginOtpRequestRequest(email), SignupResponse.class);
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        String code = MailHogReader.lastOtpCodeFor(mailhogApiBaseUrl(), email);
        ResponseEntity<AuthTokensResponse> verified = restTemplate.postForEntity(url("/auth/login/otp/verify"),
                new OtpVerifyRequest(requested.getBody().challengeId(), code), AuthTokensResponse.class);
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verified.getBody().user().mobile()).isEqualTo(mobile);
    }

    // The flexible-identifier design's whole point: typing the account's
    // MOBILE also resolves and sends the code to the account's real email.
    @Test
    void requestLoginOtpByMobileIdentifierAlsoWorksWhenTheAccountHasAnEmail() throws Exception {
        String mobile = uniqueMobile();
        String email = uniqueEmail();
        signupAndVerify(Organization.Type.BUILDER, mobile, email, "Passw0rd1");

        ResponseEntity<SignupResponse> requested = restTemplate.postForEntity(
                url("/auth/login/otp/request"), new LoginOtpRequestRequest(mobile), SignupResponse.class);
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(requested.getBody().maskedRecipient()).endsWith("@example.com");

        String code = MailHogReader.lastOtpCodeFor(mailhogApiBaseUrl(), email);
        ResponseEntity<AuthTokensResponse> verified = restTemplate.postForEntity(url("/auth/login/otp/verify"),
                new OtpVerifyRequest(requested.getBody().challengeId(), code), AuthTokensResponse.class);
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // The explicitly-required edge case: a real account with no email on
    // file (M4 team members: mobile required, email optional) must be
    // rejected clearly, not with a generic error and not by silently
    // failing to deliver a code nobody will ever see. No UI path in this
    // codebase creates such a user without an email already existing to
    // null out afterward, so this sets that up directly via SQL -- the same
    // established "direct SQL for state the app itself has no way to
    // produce" precedent used throughout this project's own test suite.
    @Test
    void requestLoginOtpFailsClearlyForAUserWithNoEmailOnFile() throws Exception {
        String mobile = uniqueMobile();
        AuthTokensResponse tokens = signupAndVerify(Organization.Type.BUILDER, mobile, uniqueEmail(), "Passw0rd1").getBody();

        // app_user has RLS (org-scoped) -- a native UPDATE with no tenant
        // context bound silently matches zero rows (no error, the GUC-less
        // policy check just evaluates false for every row), not a real
        // failure but a genuine first attempt at this test's own gotcha.
        // Bind the real org/user this account was just created under,
        // BEFORE opening the transaction (TenantAwareDataSource fixes the
        // GUC at connection checkout -- see AuthService's own extensively
        // documented "bind before the transaction opens" rule elsewhere in
        // this codebase), exactly like the app's own signup/login flows do.
        TestTenantContext.bind(tokens.org().id(), tokens.user().id(), "BUILDER", "BUILDER_ADMIN", Set.of());
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    entityManager.createNativeQuery("UPDATE app_user SET email = NULL WHERE mobile = :mobile")
                            .setParameter("mobile", mobile)
                            .executeUpdate());
        } finally {
            TestTenantContext.clear();
        }

        ResponseEntity<String> requested = restTemplate.postForEntity(
                url("/auth/login/otp/request"), new LoginOtpRequestRequest(mobile), String.class);
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(requested.getBody()).contains("error.auth.otpNoEmailOnFile");
    }

    @Test
    void forgotAndResetPasswordViaMobileOtpThenLoginWithNewPassword() throws Exception {
        String mobile = uniqueMobile();
        signupAndVerify(Organization.Type.BUILDER, mobile, uniqueEmail(), "OldPassw0rd");

        ResponseEntity<SignupResponse> forgot = restTemplate.postForEntity(
                url("/auth/password/forgot"), new ForgotPasswordRequest(mobile), SignupResponse.class);
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(forgot.getBody().challengeId()).isNotBlank();

        String code = OtpStubReader.lastCodeFor(mobile);
        ResponseEntity<Void> reset = restTemplate.postForEntity(url("/auth/password/reset"),
                new ResetPasswordRequest(null, forgot.getBody().challengeId(), code, "NewPassw0rd", "NewPassw0rd"),
                Void.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<AuthTokensResponse> loginNew = restTemplate.postForEntity(
                url("/auth/login"), new LoginRequest(mobile, "NewPassw0rd", false), AuthTokensResponse.class);
        assertThat(loginNew.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> loginOld = restTemplate.postForEntity(
                url("/auth/login"), new LoginRequest(mobile, "OldPassw0rd", false), String.class);
        assertThat(loginOld.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forgotPasswordForUnknownMobileReturnsSameShapeAsKnownMobile() {
        // Anti-enumeration: a syntactically identical, functionless
        // challengeId comes back either way — the response alone must not
        // reveal whether the account exists.
        ResponseEntity<SignupResponse> resp = restTemplate.postForEntity(
                url("/auth/password/forgot"), new ForgotPasswordRequest(uniqueMobile()), SignupResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getBody().challengeId()).isNotBlank();
        assertThat(resp.getBody().maskedRecipient()).isNotBlank();
    }

    // Regression guard for a real bug: the reset email used to send just the
    // bare token as plain text with no URL around it at all -- a real user
    // had nothing to click and nowhere in the UI to paste it, since
    // ResetPasswordPage only ever reads ?token= from the address bar.
    @Test
    void forgotPasswordViaEmailEnqueuesARealClickableResetLink() throws Exception {
        String mobile = uniqueMobile();
        String email = uniqueEmail();
        signupAndVerify(Organization.Type.BUILDER, mobile, email, "Passw0rd1");

        ResponseEntity<SignupResponse> forgot = restTemplate.postForEntity(
                url("/auth/password/forgot"), new ForgotPasswordRequest(email), SignupResponse.class);
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        List<EmailPayload> emailsToUser = outboxEventRepository.findAll().stream()
                .filter(e -> "EMAIL".equals(e.getEventType()))
                .map(this::readEmailPayload)
                .filter(p -> email.equals(p.to()))
                .toList();
        // One from signup's own welcome email, one from this forgot-password call.
        EmailPayload resetEmail = emailsToUser.stream()
                .filter(p -> p.subject().contains("Reset"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no reset-password email found for " + email));
        assertThat(resetEmail.body()).containsPattern("https?://[^\\s]+/reset-password\\?token=[A-Za-z0-9_-]+");
    }

    private EmailPayload readEmailPayload(com.shardeya.platform.OutboxEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), EmailPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void refreshRotatesTokenAndReuseOfOldTokenIsRejected() throws Exception {
        ResponseEntity<AuthTokensResponse> initial =
                signupAndVerify(Organization.Type.BUILDER, uniqueMobile(), uniqueEmail(), "Passw0rd1");
        String initialCookie = extractRefreshCookie(initial);

        ResponseEntity<AuthTokensResponse> rotated = restTemplate.exchange(
                url("/auth/refresh"), HttpMethod.POST, withCookie(initialCookie), AuthTokensResponse.class);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotatedCookie = extractRefreshCookie(rotated);
        assertThat(rotatedCookie).isNotEqualTo(initialCookie);

        // Reusing the original (now-rotated-away) refresh token over the real
        // API must be rejected.
        ResponseEntity<String> reused = restTemplate.exchange(
                url("/auth/refresh"), HttpMethod.POST, withCookie(initialCookie), String.class);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // And the family-wide revocation this triggers must also kill the
        // legitimate, never-reused rotated token.
        ResponseEntity<String> alsoDead = restTemplate.exchange(
                url("/auth/refresh"), HttpMethod.POST, withCookie(rotatedCookie), String.class);
        assertThat(alsoDead.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // The whole point of this fix: a refresh response must be self-sufficient
    // enough to restore a session from nothing (no prior in-memory user/org)
    // on a hard reload — user/org used to come back null here.
    @Test
    void refreshResponseCarriesRealUserAndOrgNotJustAnAccessToken() throws Exception {
        ResponseEntity<AuthTokensResponse> initial =
                signupAndVerify(Organization.Type.BUILDER, uniqueMobile(), uniqueEmail(), "Passw0rd1");
        String cookie = extractRefreshCookie(initial);
        AuthTokensResponse originalBody = initial.getBody();

        ResponseEntity<AuthTokensResponse> rotated = restTemplate.exchange(
                url("/auth/refresh"), HttpMethod.POST, withCookie(cookie), AuthTokensResponse.class);

        assertThat(rotated.getBody().user()).isNotNull();
        assertThat(rotated.getBody().org()).isNotNull();
        assertThat(rotated.getBody().user().mobile()).isEqualTo(originalBody.user().mobile());
        assertThat(rotated.getBody().org().id()).isEqualTo(originalBody.org().id());
    }

    @Test
    void refreshWithNoCookieAtAllIsRejected() {
        ResponseEntity<String> resp = restTemplate.postForEntity(url("/auth/refresh"), null, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
        ResponseEntity<AuthTokensResponse> tokens =
                signupAndVerify(Organization.Type.BUILDER, uniqueMobile(), uniqueEmail(), "Passw0rd1");
        String cookie = extractRefreshCookie(tokens);

        ResponseEntity<Void> logout = restTemplate.exchange(
                url("/auth/logout"), HttpMethod.POST, withCookie(cookie), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(extractMaxAgeSeconds(logout)).isEqualTo(0);

        ResponseEntity<String> afterLogout = restTemplate.exchange(
                url("/auth/refresh"), HttpMethod.POST, withCookie(cookie), String.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void meWithoutTokenIsRejected() {
        ResponseEntity<String> resp = restTemplate.getForEntity(url("/me"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void otpResendOverHttpWorksAndFourthSendIsRateLimited() throws Exception {
        String mobile = uniqueMobile();
        SignupRequest signup = new SignupRequest("RateLimit Test", mobile, uniqueEmail(), "Passw0rd1", "Passw0rd1",
                Organization.Type.BUILDER, "Pune", true);
        ResponseEntity<SignupResponse> first = restTemplate.postForEntity(url("/auth/signup"), signup, SignupResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String challengeId = first.getBody().challengeId();

        // Resend #2 and #3 count toward the 3/hour cap too (signup itself was #1).
        ResponseEntity<String> resendTooSoon = restTemplate.postForEntity(
                url("/auth/otp/resend"), new OtpResendRequest(challengeId), String.class);
        assertThat(resendTooSoon.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS); // cooldown, not the hourly cap
    }
}
