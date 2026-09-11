package com.shardeya.foundation.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardeya.foundation.auth.dto.AuthTokensResponse;
import com.shardeya.foundation.auth.dto.ForgotPasswordRequest;
import com.shardeya.foundation.auth.dto.LoginOtpRequestRequest;
import com.shardeya.foundation.auth.dto.LoginRequest;
import com.shardeya.foundation.auth.dto.OrgSummary;
import com.shardeya.foundation.auth.dto.OtpResendRequest;
import com.shardeya.foundation.auth.dto.OtpVerifyRequest;
import com.shardeya.foundation.auth.dto.ResetPasswordRequest;
import com.shardeya.foundation.auth.dto.SignupRequest;
import com.shardeya.foundation.auth.dto.SignupResponse;
import com.shardeya.foundation.auth.dto.UserSummary;
import com.shardeya.foundation.rbac.Role;
import com.shardeya.foundation.rbac.RoleRepository;
import com.shardeya.foundation.rbac.UserProjectAccessRepository;
import com.shardeya.foundation.subscription.Subscription;
import com.shardeya.foundation.subscription.SubscriptionRepository;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.JwtService;
import com.shardeya.platform.OutboxService;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.platform.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates M-01's whole flow. See CLAUDE.md "Milestone 1" notes for the
 * scope trims (no email/WhatsApp for new-device-login or account-locked
 * notifications; no password-rehash-on-legacy-hash since every hash in
 * existence is already Argon2id from day one).
 */
@Service
public class AuthService {

    /**
     * Internal transport shape between this service and {@link AuthController}
     * only -- {@code refreshToken}/{@code refreshExpiresAt} never leave this
     * pair of classes. The controller writes them into an httpOnly Set-Cookie
     * header and returns {@code body} (which has no refresh-token field at
     * all) as the actual JSON response. Every method that used to return a
     * plain {@link AuthTokensResponse} (which used to carry the raw refresh
     * token as a JSON field) now returns this instead.
     */
    public record TokenIssueResult(AuthTokensResponse body, String refreshToken, Instant refreshExpiresAt) {
    }

    private static final int MAX_FAILURES_BEFORE_LOCK = 5;
    private static final Duration BASE_LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration MAX_LOCK_DURATION = Duration.ofHours(24);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private final OtpService otpService;
    private final AuthLookupRepository authLookupRepository;
    private final OrganizationRepository organizationRepository;
    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserProjectAccessRepository userProjectAccessRepository;
    private final com.shardeya.builder.team.StaffInviteRepository staffInviteRepository;
    private final com.shardeya.builder.broker.BrokerTierRepository brokerTierRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final TenantContextBinder tenantContextBinder;
    private final OutboxService outboxService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    // The reset-password email used to send a bare token with no URL around
    // it at all -- a real user had nothing to click and nowhere in the UI to
    // paste it, since ResetPasswordPage only ever reads ?token= from the
    // address bar. Same default as CorsConfig's own allowed-origins, since
    // in dev the frontend really is at that origin.
    private final String frontendBaseUrl;
    private final SecureRandom random = new SecureRandom();
    // Not @Transactional: JpaTransactionManager (configured with a DataSource
    // for JDBC/JPA synchronization, which this app needs since
    // AuthLookupRepository is plain JDBC) checks out and fixes the physical
    // connection's RLS GUC in doBegin() — BEFORE the method body runs, not on
    // first query as Hibernate's own lazy-acquisition setting would suggest.
    // Confirmed by instrumenting TenantAwareDataSource: getConnection() fired
    // with tenant=null one millisecond before the method body's first log
    // line, and no second getConnection() call ever happened after
    // TenantContextBinder.bindNewOrgContext() ran — Hibernate just reused the
    // already-checked-out, already-"no context" connection for the whole
    // transaction. No amount of reordering statements inside a @Transactional
    // method body can fix that; the bind has to happen in plain Java before
    // any transaction (and therefore any connection checkout) starts at all.
    // Same fix already proven in RefreshTokenService's reuse-detection path.
    private final TransactionTemplate requiresNewTransaction;

    public AuthService(OtpService otpService, AuthLookupRepository authLookupRepository,
                        OrganizationRepository organizationRepository, AppUserRepository appUserRepository,
                        RoleRepository roleRepository, SubscriptionRepository subscriptionRepository,
                        RefreshTokenService refreshTokenService, UserProjectAccessRepository userProjectAccessRepository,
                        com.shardeya.builder.team.StaffInviteRepository staffInviteRepository,
                        com.shardeya.builder.broker.BrokerTierRepository brokerTierRepository,
                        JwtService jwtService,
                        PasswordEncoder passwordEncoder, TenantContextBinder tenantContextBinder,
                        OutboxService outboxService, StringRedisTemplate redis, ObjectMapper objectMapper,
                        PlatformTransactionManager transactionManager,
                        @Value("${shardeya.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.otpService = otpService;
        this.authLookupRepository = authLookupRepository;
        this.organizationRepository = organizationRepository;
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.refreshTokenService = refreshTokenService;
        this.userProjectAccessRepository = userProjectAccessRepository;
        this.staffInviteRepository = staffInviteRepository;
        this.brokerTierRepository = brokerTierRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.tenantContextBinder = tenantContextBinder;
        this.outboxService = outboxService;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.frontendBaseUrl = frontendBaseUrl;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    // ---------------------------------------------------------------- signup

    public SignupResponse signup(SignupRequest req, String ip) {
        if (!req.password().equals(req.confirmPassword())) {
            throw new BadRequestException("confirmPassword", "PASSWORD_MISMATCH", "error.password.mismatch");
        }
        // M-01 §10: don't reveal *which* field collided beyond "registered" —
        // still two distinct keys since the UI needs to know which field to
        // point at, but neither leaks role/org existence.
        if (authLookupRepository.existsByMobile(req.mobile())) {
            throw new ConflictException("error.auth.mobileRegistered");
        }
        if (authLookupRepository.existsByEmail(req.email())) {
            throw new ConflictException("error.auth.emailRegistered");
        }

        String passwordHash = passwordEncoder.encode(req.password());
        PendingSignup pending = new PendingSignup(
                req.fullName(), req.mobile(), req.email(), passwordHash, req.role(), req.city());
        // Email OTP fix: signup verification is delivered by email now, not
        // SMS (the SMS adapter is still stubbed pending DLT registration --
        // see OtpService's own javadoc). req.email() is already @NotBlank on
        // SignupRequest, so this was already a hard requirement before this
        // change; it just wasn't the delivery channel until now.
        OtpService.ChallengeResult challenge = otpService.create(
                OtpService.PURPOSE_SIGNUP, OtpService.Channel.EMAIL, req.email(), ip, writeJson(pending));
        return new SignupResponse(challenge.challengeId(), challenge.maskedRecipient(), challenge.resendAfterSeconds());
    }

    public TokenIssueResult verifySignupOtp(OtpVerifyRequest req, String userAgent, String ip) {
        OtpService.VerifyResult verified = otpService.verify(req.challengeId(), req.code());
        if (!OtpService.PURPOSE_SIGNUP.equals(verified.purpose())) {
            throw new BadRequestException("challengeId", "OTP_WRONG_PURPOSE", "error.otp.invalid");
        }
        PendingSignup pending = readJson(verified.payloadJson(), PendingSignup.class);

        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String roleCode = pending.role() == Organization.Type.BUILDER ? "BUILDER_ADMIN" : "BROKER_OWNER";

        // Bind in plain Java, BEFORE opening the transaction below — see this
        // class's requiresNewTransaction field comment for why: starting a
        // @Transactional-annotated method (or a TransactionTemplate) is what
        // checks out the physical connection and fixes its RLS GUC, so the
        // bind has to happen strictly before that call, not just early inside it.
        tenantContextBinder.bindNewOrgContext(orgId, userId, pending.role().name(), roleCode, Set.of());
        try {
            return requiresNewTransaction.execute(status -> {
                Role role = roleRepository.findByCodeAndOrgIdIsNull(roleCode).orElseThrow();

                Organization org = new Organization(orgId, pending.role(), orgNameFor(pending), pending.city());
                organizationRepository.save(org);

                AppUser user = new AppUser(userId, orgId, pending.fullName(), pending.mobile(), role, true);
                user.setEmail(pending.email());
                user.setPasswordHash(pending.passwordHash());
                user.setMobileVerifiedAt(Instant.now());
                user.setStatus(AppUser.Status.ACTIVE);
                user.setLastLoginAt(Instant.now());
                appUserRepository.save(user);

                Subscription subscription = new Subscription(
                        UUID.randomUUID(), orgId, "FREE", Subscription.Status.ACTIVE,
                        Instant.now().plus(36500, ChronoUnit.DAYS));
                subscriptionRepository.save(subscription);

                // B-14 §20.4 default tiers ("Bronze 0-2, Silver 3-9, Gold
                // 10-24, Platinum 25+") -- V6_012's own migration comment
                // already claimed this happens here ("new orgs get the
                // identical 4 rows seeded at signup time instead"), but that
                // was never actually wired up until now; every BUILDER org
                // created between M6 shipping and this fix would have had
                // zero broker_tier rows, silently disabling tier
                // auto-upgrade entirely for them. Broker-persona orgs don't
                // use broker_tier at all, so this only runs for BUILDER.
                if (pending.role() == Organization.Type.BUILDER) {
                    seedDefaultBrokerTiers(orgId);
                }

                outboxService.enqueueEmail(
                        orgId, "app_user", userId, pending.email(),
                        "Welcome to Shardeya", "Your account is ready. Welcome aboard!");

                return issueTokens(userId, orgId, pending.role().name(), roleCode, role.getPermissionCodes(),
                        pending.fullName(), pending.mobile(), pending.email(), true, "en", org, false, userAgent, ip,
                        true, List.of());
            });
        } finally {
            tenantContextBinder.clear();
        }
    }

    // B-14 §20.4 default tiers (Business Executive through President)
    // aligned with designation slabs.
    private void seedDefaultBrokerTiers(UUID orgId) {
        record DefaultTier(String name, String nameHi, int minDeals, Integer maxDeals, short sortOrder) {
        }
        List<DefaultTier> defaults = List.of(
                new DefaultTier("Business Executive", "बिज़नेस एक्ज़िक्यूटिव", 0, 0, (short) 1),
                new DefaultTier("Senior Business Executive", "सीनियर बिज़नेस एक्ज़िक्यूटिव", 1, 1, (short) 2),
                new DefaultTier("Business Development Officer", "बिज़नेस डेवलपमेंट ऑफिसर", 2, 2, (short) 3),
                new DefaultTier("Business Manager", "बिज़नेस मैनेजर", 3, 5, (short) 4),
                new DefaultTier("Assistant Sales Director", "असिस्टेंट सेल्स डायरेक्टर", 6, 9, (short) 5),
                new DefaultTier("Sales Director", "सेल्स डायरेक्टर", 10, 14, (short) 6),
                new DefaultTier("Vice President", "वाइस प्रेसिडेंट", 15, 19, (short) 7),
                new DefaultTier("President", "प्रेसिडेंट", 20, null, (short) 8));
        for (DefaultTier d : defaults) {
            brokerTierRepository.save(new com.shardeya.builder.broker.BrokerTier(
                    UUID.randomUUID(), orgId, d.name(), d.nameHi(), d.minDeals(), d.maxDeals(), d.sortOrder()));
        }
    }

    public SignupResponse resendOtp(OtpResendRequest req, String ip) {
        OtpService.ChallengeResult resent = otpService.resend(req.challengeId(), ip);
        return new SignupResponse(resent.challengeId(), resent.maskedRecipient(), resent.resendAfterSeconds());
    }

    // ----------------------------------------------------------------- login

    public TokenIssueResult login(LoginRequest req, String userAgent, String ip) {
        AuthLookupUser lookup = resolveIdentifier(req.identifier())
                .orElseThrow(() -> new UnauthorizedException("error.auth.invalidCredentials"));

        tenantContextBinder.bindNewOrgContext(lookup.orgId(), lookup.id(), null, lookup.roleCode(), Set.of());
        try {
            return requiresNewTransaction.execute(status -> {
                AppUser user = appUserRepository.findById(lookup.id()).orElseThrow();

                if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
                    throw new UnauthorizedException("error.auth.accountLocked");
                }
                if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
                    recordFailedLogin(user);
                    throw new UnauthorizedException("error.auth.invalidCredentials");
                }

                recordSuccessfulLogin(user);
                Organization org = organizationRepository.findById(lookup.orgId()).orElseThrow();
                Role role = user.getRole();
                boolean allProjects = user.getProjectAccessMode() == AppUser.ProjectAccessMode.ALL;
                List<UUID> projectScope = allProjects ? List.of() : projectScopeOf(user);
                return issueTokens(user.getId(), user.getOrgId(), org.getType().name(), role.getCode(),
                        role.getPermissionCodes(), user.getFullName(), user.getMobile(), user.getEmail(),
                        user.isOwner(), user.getLanguage(), org, req.rememberMe(), userAgent, ip,
                        allProjects, projectScope);
            });
        } finally {
            tenantContextBinder.clear();
        }
    }

    /**
     * Email OTP fix: "login with OTP" is delivered by email now, not SMS.
     * The identifier field stays flexible (mobile OR email, exactly like
     * password login's {@code resolveIdentifier} and forgot-password's own
     * identifier field) rather than forcing an email-only input — that's
     * what makes the "staff member with no email on file" edge case
     * actually reachable and given a clear answer, instead of a real
     * account with no email simply never being distinguishable from a
     * typo'd identifier. M4 team members are created with mobile required,
     * email optional (03-BUILDER-MODULES.md B-12) — such a user typing
     * their own mobile here resolves to a real account that genuinely has
     * no email to send a code to, and gets told exactly that rather than a
     * generic "invalid credentials" or a silently-undeliverable code.
     */
    public SignupResponse requestLoginOtp(LoginOtpRequestRequest req, String ip) {
        AuthLookupUser lookup = resolveIdentifier(req.identifier())
                .orElseThrow(() -> new UnauthorizedException("error.auth.invalidCredentials"));
        if (lookup.email() == null || lookup.email().isBlank()) {
            throw new BadRequestException("identifier", "OTP_EMAIL_NOT_AVAILABLE", "error.auth.otpNoEmailOnFile");
        }
        OtpService.ChallengeResult challenge = otpService.create(
                OtpService.PURPOSE_LOGIN, OtpService.Channel.EMAIL, lookup.email(), ip, lookup.id().toString());
        return new SignupResponse(challenge.challengeId(), challenge.maskedRecipient(), challenge.resendAfterSeconds());
    }

    public TokenIssueResult verifyLoginOtp(OtpVerifyRequest req, String userAgent, String ip) {
        OtpService.VerifyResult verified = otpService.verify(req.challengeId(), req.code());
        if (!OtpService.PURPOSE_LOGIN.equals(verified.purpose())) {
            throw new BadRequestException("challengeId", "OTP_WRONG_PURPOSE", "error.otp.invalid");
        }
        UUID userId = UUID.fromString(verified.payloadJson());
        AuthLookupUser lookup = authLookupRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("error.auth.invalidCredentials"));

        tenantContextBinder.bindNewOrgContext(lookup.orgId(), lookup.id(), null, lookup.roleCode(), Set.of());
        try {
            return requiresNewTransaction.execute(status -> {
                AppUser user = appUserRepository.findById(lookup.id()).orElseThrow();
                recordSuccessfulLogin(user);
                Organization org = organizationRepository.findById(lookup.orgId()).orElseThrow();
                Role role = user.getRole();
                boolean allProjects = user.getProjectAccessMode() == AppUser.ProjectAccessMode.ALL;
                List<UUID> projectScope = allProjects ? List.of() : projectScopeOf(user);
                return issueTokens(user.getId(), user.getOrgId(), org.getType().name(), role.getCode(),
                        role.getPermissionCodes(), user.getFullName(), user.getMobile(), user.getEmail(),
                        user.isOwner(), user.getLanguage(), org, false, userAgent, ip,
                        allProjects, projectScope);
            });
        } finally {
            tenantContextBinder.clear();
        }
    }

    // ------------------------------------------------------ forgot / reset

    /**
     * The mobile path's response MUST carry a challengeId regardless of
     * whether the identifier exists — the frontend needs one to submit
     * alongside whatever code the user types into the OTP boxes, and
     * returning a real one only when the account exists (nothing when it
     * doesn't) would itself be the exact enumeration oracle M-01 §7 forbids.
     * A non-existent mobile gets a syntactically identical but functionless
     * challengeId instead, so the response is indistinguishable either way.
     * The email path never exposes a challengeId at all (reset there uses an
     * emailed token instead), so its ack is uniform and empty regardless.
     */
    public SignupResponse forgotPassword(ForgotPasswordRequest req, String ip) {
        boolean isMobile = req.identifier().matches("^\\d{10}$");
        var lookup = resolveIdentifier(req.identifier());

        if (isMobile) {
            if (lookup.isPresent()) {
                // Unaffected by the Email OTP fix -- password-reset-via-mobile
                // stays exactly SMS-delivered, explicitly, since forgotPassword's
                // own mobile/email branch is what already decides the channel
                // here (the email branch below uses an emailed opaque token
                // instead of this OTP mechanism entirely).
                OtpService.ChallengeResult challenge = otpService.create(
                        OtpService.PURPOSE_RESET, OtpService.Channel.SMS, lookup.get().mobile(), ip, lookup.get().id().toString());
                return new SignupResponse(challenge.challengeId(), challenge.maskedRecipient(), challenge.resendAfterSeconds());
            }
            return new SignupResponse(UUID.randomUUID().toString(), maskMobile(req.identifier()), 60);
        }

        lookup.ifPresent(user -> {
            String token = generateOpaqueToken();
            redis.opsForValue().set("reset:token:" + token, user.id().toString(), RESET_TOKEN_TTL);
            // A real, clickable URL -- ResetPasswordPage only ever reads the
            // token from ?token= in the address bar, there's no field to
            // paste one into. The old body sent just the bare token with no
            // link at all, which meant a real user opening this email had
            // nothing to click and nowhere to type it.
            String link = frontendBaseUrl + "/reset-password?token=" + token;
            outboxService.enqueueEmail(user.orgId(), "app_user", user.id(), user.email(), "Reset your Shardeya password",
                    "Click the link below to reset your password:\n" + link
                            + "\nThis link expires in 30 minutes and can only be used once.");
        });
        return new SignupResponse(null, null, 0);
    }

    public void resetPassword(ResetPasswordRequest req) {
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new BadRequestException("confirmPassword", "PASSWORD_MISMATCH", "error.password.mismatch");
        }

        UUID userId;
        if (req.token() != null && !req.token().isBlank()) {
            String key = "reset:token:" + req.token();
            String stored = redis.opsForValue().get(key);
            if (stored == null) {
                throw new BadRequestException("token", "RESET_TOKEN_INVALID", "error.auth.resetTokenInvalid");
            }
            redis.delete(key);
            userId = UUID.fromString(stored);
        } else if (req.challengeId() != null && req.code() != null) {
            OtpService.VerifyResult verified = otpService.verify(req.challengeId(), req.code());
            if (!OtpService.PURPOSE_RESET.equals(verified.purpose())) {
                throw new BadRequestException("challengeId", "OTP_WRONG_PURPOSE", "error.otp.invalid");
            }
            userId = UUID.fromString(verified.payloadJson());
        } else {
            throw new BadRequestException("token", "RESET_REQUEST_INVALID", "error.auth.resetTokenInvalid");
        }

        AuthLookupUser lookup = authLookupRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("token", "RESET_TOKEN_INVALID", "error.auth.resetTokenInvalid"));

        tenantContextBinder.bindNewOrgContext(lookup.orgId(), lookup.id(), null, lookup.roleCode(), Set.of());
        try {
            requiresNewTransaction.executeWithoutResult(status -> {
                AppUser user = appUserRepository.findById(userId).orElseThrow();
                user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
                user.setFailedLoginCount((short) 0);
                user.setLockedUntil(null);
                user.bumpTokenVersion();
                refreshTokenService.revokeAllForUser(userId);
                if (user.getEmail() != null) {
                    outboxService.enqueueEmail(lookup.orgId(), "app_user", userId, user.getEmail(), "Your password was changed",
                            "Your Shardeya password was just changed. If this wasn't you, contact support immediately.");
                }
            });
        } finally {
            tenantContextBinder.clear();
        }
    }

    /**
     * B-12 §7: the staff-invite counterpart to resetPassword -- same
     * Redis-opaque-token shape (mint on send, look up here, delete on use),
     * because accepting an invite has the identical "anonymous request,
     * resolve to a user before any tenant context exists" shape a mobile-OTP
     * reset already solved. Doesn't auto-login afterward (B-12 §8's own user
     * flow is "sets password -> logs in", a separate step) -- consistent
     * with resetPassword's own void return.
     */
    public void acceptInvite(com.shardeya.foundation.auth.dto.AcceptInviteRequest req) {
        if (!req.password().equals(req.confirmPassword())) {
            throw new BadRequestException("confirmPassword", "PASSWORD_MISMATCH", "error.password.mismatch");
        }
        String key = "invite:token:" + req.token();
        String stored = redis.opsForValue().get(key);
        if (stored == null) {
            throw new BadRequestException("token", "INVITE_TOKEN_INVALID", "error.auth.inviteTokenInvalid");
        }
        UUID userId = UUID.fromString(stored);

        AuthLookupUser lookup = authLookupRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("token", "INVITE_TOKEN_INVALID", "error.auth.inviteTokenInvalid"));

        tenantContextBinder.bindNewOrgContext(lookup.orgId(), lookup.id(), null, lookup.roleCode(), Set.of());
        try {
            requiresNewTransaction.executeWithoutResult(status -> {
                AppUser user = appUserRepository.findById(userId).orElseThrow();
                if (user.getStatus() != AppUser.Status.INVITED) {
                    throw new BadRequestException("token", "INVITE_TOKEN_INVALID", "error.auth.inviteTokenInvalid");
                }
                user.setPasswordHash(passwordEncoder.encode(req.password()));
                user.setStatus(AppUser.Status.ACTIVE);
                appUserRepository.save(user);

                staffInviteRepository.findFirstByAppUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                        .ifPresent(invite -> {
                            invite.setAcceptedAt(Instant.now());
                            staffInviteRepository.save(invite);
                        });

                outboxService.enqueueNotification(lookup.orgId(), "INVITE_ACCEPTED", "notification.inviteAccepted",
                        null, Map.of("name", user.getFullName()), "app_user", userId);
            });
            redis.delete(key);
        } finally {
            tenantContextBinder.clear();
        }
    }

    // --------------------------------------------------------- refresh/logout

    public TokenIssueResult refresh(String rawRefreshToken, String userAgent, String ip) {
        RefreshTokenService.IssueResult rotated = refreshTokenService.rotate(rawRefreshToken, userAgent, ip);
        AuthLookupUser lookup = authLookupRepository.findById(rotated.userId())
                .orElseThrow(() -> new UnauthorizedException("error.auth.refreshInvalid"));
        // Organization has no RLS (it IS the tenant root — see its migration's
        // own comment), so this is safe to read with no tenant context bound.
        Organization org = organizationRepository.findById(lookup.orgId())
                .orElseThrow(() -> new UnauthorizedException("error.auth.refreshInvalid"));

        String accessToken = jwtService.issueAccessToken(new JwtService.AccessTokenClaims(
                lookup.id(), lookup.orgId(), org.getType().name(), lookup.roleCode(), lookup.permissions(),
                lookup.tokenVersion(), lookup.allProjects(), lookup.projectScope()));

        // user/org used to come back null here (nothing needed them — the
        // frontend's in-memory store already had a copy from the original
        // login/signup, since a hard reload always logged everyone out
        // anyway). Now that the refresh token lives in an httpOnly cookie and
        // a hard reload attempts a silent /refresh to restore the session
        // from nothing, this response has to be self-sufficient: RequireAuth
        // gates on accessToken *and* org both being present, so a null org
        // here would make a successful cookie-based refresh still bounce the
        // user to /login. Everything needed is already in `lookup`/`org` —
        // no extra query.
        UserSummary userSummary = new UserSummary(lookup.id(), lookup.fullName(), lookup.mobile(), lookup.email(),
                lookup.roleCode(), lookup.owner(), lookup.language());
        OrgSummary orgSummary = new OrgSummary(org.getId(), org.getType().name(), org.getName(), org.getCity());
        AuthTokensResponse body = new AuthTokensResponse(
                accessToken, JwtService.ACCESS_TOKEN_TTL.toSeconds(), userSummary, orgSummary);
        return new TokenIssueResult(body, rotated.rawToken(), rotated.expiresAt());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revokeFamilyOf(rawRefreshToken);
        }
    }

    // ------------------------------------------------------------- helpers

    private List<UUID> projectScopeOf(AppUser user) {
        return userProjectAccessRepository.findByOrgIdAndUserId(user.getOrgId(), user.getId())
                .stream().map(com.shardeya.foundation.rbac.UserProjectAccess::getProjectId).toList();
    }

    private java.util.Optional<AuthLookupUser> resolveIdentifier(String identifier) {
        if (identifier.matches("^\\d{10}$")) {
            return authLookupRepository.findByMobile(identifier);
        }
        return authLookupRepository.findByEmail(identifier);
    }

    private String maskMobile(String mobile) {
        if (mobile.length() <= 2) {
            return mobile;
        }
        return "X".repeat(mobile.length() - 2) + mobile.substring(mobile.length() - 2);
    }

    private void recordFailedLogin(AppUser user) {
        int newCount = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount((short) newCount);
        if (newCount >= MAX_FAILURES_BEFORE_LOCK && newCount % MAX_FAILURES_BEFORE_LOCK == 0) {
            int doublings = (newCount / MAX_FAILURES_BEFORE_LOCK) - 1;
            Duration lockDuration = BASE_LOCK_DURATION.multipliedBy(1L << Math.min(doublings, 6));
            if (lockDuration.compareTo(MAX_LOCK_DURATION) > 0) {
                lockDuration = MAX_LOCK_DURATION;
            }
            user.setLockedUntil(Instant.now().plus(lockDuration));
        }
    }

    private void recordSuccessfulLogin(AppUser user) {
        user.setFailedLoginCount((short) 0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
    }

    private TokenIssueResult issueTokens(UUID userId, UUID orgId, String orgType, String roleCode,
                                          Set<String> permissions, String fullName, String mobile, String email,
                                          boolean isOwner, String language, Organization org, boolean rememberMe,
                                          String userAgent, String ip, boolean allProjects, List<UUID> projectScope) {
        String accessToken = jwtService.issueAccessToken(new JwtService.AccessTokenClaims(
                userId, orgId, orgType, roleCode, List.copyOf(permissions), 0, allProjects, projectScope));
        RefreshTokenService.IssueResult refreshToken = refreshTokenService.issue(userId, rememberMe, userAgent, ip);

        UserSummary userSummary = new UserSummary(userId, fullName, mobile, email, roleCode, isOwner, language);
        OrgSummary orgSummary = new OrgSummary(org.getId(), org.getType().name(), org.getName(), org.getCity());
        AuthTokensResponse body = new AuthTokensResponse(
                accessToken, JwtService.ACCESS_TOKEN_TTL.toSeconds(), userSummary, orgSummary);
        return new TokenIssueResult(body, refreshToken.rawToken(), refreshToken.expiresAt());
    }

    private String orgNameFor(PendingSignup pending) {
        return pending.fullName();
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
