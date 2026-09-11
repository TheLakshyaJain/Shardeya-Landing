package com.shardeya.builder.team;

import com.shardeya.builder.team.dto.DeactivateRequest;
import com.shardeya.builder.team.dto.StaffActivityResponse;
import com.shardeya.builder.team.dto.TeamMemberCreateRequest;
import com.shardeya.builder.team.dto.TeamMemberResponse;
import com.shardeya.builder.team.dto.TeamMemberUpdateRequest;
import com.shardeya.foundation.auth.AppUser;
import com.shardeya.foundation.auth.AppUserRepository;
import com.shardeya.foundation.auth.AuthLookupRepository;
import com.shardeya.foundation.auth.RefreshTokenService;
import com.shardeya.foundation.auth.SmsGateway;
import com.shardeya.foundation.customer.CustomerRepository;
import com.shardeya.foundation.customer.CustomerService;
import com.shardeya.foundation.customer.InteractionRepository;
import com.shardeya.foundation.rbac.Role;
import com.shardeya.foundation.rbac.RoleRepository;
import com.shardeya.foundation.rbac.UserProjectAccess;
import com.shardeya.foundation.rbac.UserProjectAccessRepository;
import com.shardeya.foundation.subscription.EntitlementService;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.ForbiddenException;
import com.shardeya.platform.OutboxService;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * B-12 Admin Panel / Team & Roles. Ownership transfer (§7 "two-step, OTP
 * confirmed") is a documented, deliberate scope trim -- none of Milestone
 * 4's own exit criteria exercise it, and it's a materially separate flow
 * (initiate/confirm across two different users) from everything else this
 * class does; add it as its own piece of work if a future milestone needs
 * it, rather than build it speculatively here.
 */
@Service
public class TeamService {

    private static final Set<String> STAFF_ROLE_CODES = Set.of("MANAGER", "SALES_EXECUTIVE", "ACCOUNTS_STAFF", "VIEW_ONLY");
    private static final Duration INVITE_TTL = Duration.ofDays(7);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private final AppUserRepository appUserRepository;
    private final AuthLookupRepository authLookupRepository;
    private final RoleRepository roleRepository;
    private final UserProjectAccessRepository userProjectAccessRepository;
    private final StaffInviteRepository staffInviteRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final InteractionRepository interactionRepository;
    private final EntitlementService entitlementService;
    private final RefreshTokenService refreshTokenService;
    private final OutboxService outboxService;
    private final TenantContextBinder tenantContextBinder;
    private final StringRedisTemplate redis;
    private final SmsGateway smsGateway;
    private final EntityManager entityManager;
    // The invite link used to be a bare relative path with no real host at
    // all ("/accept-invite?token=...") -- meaningless in an SMS, and in an
    // email only "worked" if the client happened to guess it was relative to
    // the frontend's own origin. Same default as CorsConfig's own
    // allowed-origins.
    private final String frontendBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public TeamService(AppUserRepository appUserRepository, AuthLookupRepository authLookupRepository,
                        RoleRepository roleRepository, UserProjectAccessRepository userProjectAccessRepository,
                        StaffInviteRepository staffInviteRepository, CustomerRepository customerRepository,
                        CustomerService customerService,
                        InteractionRepository interactionRepository, EntitlementService entitlementService,
                        RefreshTokenService refreshTokenService, OutboxService outboxService,
                        TenantContextBinder tenantContextBinder, StringRedisTemplate redis, SmsGateway smsGateway,
                        EntityManager entityManager,
                        @Value("${shardeya.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.entityManager = entityManager;
        this.frontendBaseUrl = frontendBaseUrl;
        this.appUserRepository = appUserRepository;
        this.authLookupRepository = authLookupRepository;
        this.roleRepository = roleRepository;
        this.userProjectAccessRepository = userProjectAccessRepository;
        this.staffInviteRepository = staffInviteRepository;
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.interactionRepository = interactionRepository;
        this.entitlementService = entitlementService;
        this.refreshTokenService = refreshTokenService;
        this.outboxService = outboxService;
        this.tenantContextBinder = tenantContextBinder;
        this.redis = redis;
        this.smsGateway = smsGateway;
    }

    @Transactional
    public TeamMemberResponse create(TeamMemberCreateRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();
        entitlementService.assertWithinQuota(orgId, "BUILDER_TEAM_MEMBERS", null, "error.team.quotaExceeded");

        if (!STAFF_ROLE_CODES.contains(req.roleCode())) {
            throw new BadRequestException("roleCode", "ROLE_INVALID", "error.team.roleInvalid");
        }
        Role role = roleRepository.findByCodeAndOrgIdIsNull(req.roleCode())
                .orElseThrow(() -> new BadRequestException("roleCode", "ROLE_INVALID", "error.team.roleInvalid"));

        // Global uniqueness (app_user.mobile/email are NOT per-org -- V0_003's
        // own "a person is one login" decision) -- same BYPASSRLS lookup
        // AuthService.signup() uses, since this org's own RLS-scoped
        // repository can't see rows in other orgs to detect the collision.
        if (authLookupRepository.existsByMobile(req.mobile())) {
            throw new ConflictException("error.team.mobileRegistered");
        }
        if (req.email() != null && !req.email().isBlank() && authLookupRepository.existsByEmail(req.email())) {
            throw new ConflictException("error.team.emailRegistered");
        }

        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, orgId, req.fullName(), req.mobile(), role, false);
        user.setEmail(req.email());
        user.setStatus(AppUser.Status.INVITED);
        user.setCreatedBy(tenantContextBinder.current().userId());
        if (req.projectAccess() != null && !req.projectAccess().isEmpty()) {
            user.setProjectAccessMode(AppUser.ProjectAccessMode.SCOPED);
        }
        user = appUserRepository.save(user);
        // AppUser.id is assigned in Java (no @GeneratedValue) -- save()
        // routes through merge(), and the returned reference's
        // @CreationTimestamp (createdAt, read by toResponse() below) isn't
        // reliably populated without an explicit flush+refresh. Same bug
        // class found (and NPE-crashing) in InteractionService; here it
        // would have just silently shipped createdAt: null in the response.
        entityManager.flush();
        entityManager.refresh(user);
        applyProjectAccess(orgId, userId, req.projectAccess());

        if (req.sendInvite()) {
            sendInvite(orgId, user, false);
        }

        outboxService.enqueueNotification(orgId, "STAFF_ADDED", "notification.staffAdded", null,
                Map.of("name", user.getFullName(), "role", role.getNameEn()), "app_user", userId);

        return toResponse(user);
    }

    // AppUser.role is @ManyToOne(fetch = LAZY) (mirrors the DB FK, not
    // eagerly joined) -- toResponse() reads user.getRole().getCode(), which
    // needs an open Hibernate session at the point it's actually accessed,
    // not just at query time. Without @Transactional here the repository
    // call's session closes the moment findByOrgIdAndDeletedAtIsNull()
    // returns, and the .map(this::toResponse) that runs afterward throws
    // LazyInitializationException ("no Session") the instant it touches the
    // role proxy -- a 500 with no obvious connection to "forgot
    // @Transactional" from the stack trace alone. Same root-cause family as
    // this project's other missing-@Transactional-on-a-read bugs
    // (PaymentService.summary(), EntitlementService's own refresh() calls).
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> list(String statusFilter, String roleFilter) {
        UUID orgId = tenantContextBinder.currentOrgId();
        return appUserRepository.findByOrgIdAndDeletedAtIsNull(orgId).stream()
                .filter(u -> statusFilter == null || u.getStatus().name().equals(statusFilter))
                .filter(u -> roleFilter == null || u.getRole().getCode().equals(roleFilter))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamMemberResponse get(UUID userId) {
        return toResponse(requireMember(userId));
    }

    @Transactional
    public TeamMemberResponse update(UUID userId, TeamMemberUpdateRequest req) {
        AppUser user = requireMember(userId);
        assertNotSelfOrOwner(user, "error.team.cannotEditOwner");

        if (req.fullName() != null) user.setFullName(req.fullName());
        if (req.email() != null) user.setEmail(req.email());
        boolean roleChanged = false;
        if (req.roleCode() != null && !req.roleCode().equals(user.getRole().getCode())) {
            if (!STAFF_ROLE_CODES.contains(req.roleCode())) {
                throw new BadRequestException("roleCode", "ROLE_INVALID", "error.team.roleInvalid");
            }
            Role newRole = roleRepository.findByCodeAndOrgIdIsNull(req.roleCode())
                    .orElseThrow(() -> new BadRequestException("roleCode", "ROLE_INVALID", "error.team.roleInvalid"));
            user.setRole(newRole);
            roleChanged = true;
        }
        if (Boolean.TRUE.equals(req.allProjects())) {
            user.setProjectAccessMode(AppUser.ProjectAccessMode.ALL);
            userProjectAccessRepository.deleteByOrgIdAndUserId(user.getOrgId(), userId);
        } else if (req.projectAccess() != null) {
            user.setProjectAccessMode(AppUser.ProjectAccessMode.SCOPED);
            userProjectAccessRepository.deleteByOrgIdAndUserId(user.getOrgId(), userId);
            applyProjectAccess(user.getOrgId(), userId, req.projectAccess());
        }
        // M-02 §7: role/scope changes take effect within the access token's
        // own 15-minute TTL; bumping token_version forces it immediately via
        // the same denylist-equivalent check RefreshTokenService already
        // uses for revocation, rather than making the affected user wait.
        if (roleChanged) {
            user.bumpTokenVersion();
        }
        user = appUserRepository.save(user);

        if (roleChanged) {
            outboxService.enqueueNotificationForUser(user.getOrgId(), userId, "STAFF_ADDED", "notification.roleChanged",
                    null, Map.of("role", user.getRole().getNameEn()), "app_user", userId);
        }
        return toResponse(user);
    }

    @Transactional
    public void deactivate(UUID userId, DeactivateRequest req) {
        AppUser user = requireMember(userId);
        assertNotSelfOrOwner(user, "error.team.cannotDeactivateOwner");
        handleReassignment(user, req.reassignToUserId(), req.leaveUnassigned());

        user.setStatus(AppUser.Status.INACTIVE);
        user.bumpTokenVersion();
        appUserRepository.save(user);
        refreshTokenService.revokeAllForUser(userId);

        outboxService.enqueueNotificationForUser(user.getOrgId(), userId, "STAFF_DEACTIVATED",
                "notification.staffDeactivated", null, Map.of(), "app_user", userId);
    }

    @Transactional
    public void reactivate(UUID userId) {
        AppUser user = requireMember(userId);
        assertNotSelfOrOwner(user, "error.team.cannotEditOwner");
        if (user.getStatus() != AppUser.Status.INACTIVE) {
            throw new BadRequestException("status", "NOT_INACTIVE", "error.team.notInactive");
        }
        user.setStatus(AppUser.Status.ACTIVE);
        appUserRepository.save(user);
    }

    @Transactional
    public void remove(UUID userId, DeactivateRequest req) {
        AppUser user = requireMember(userId);
        assertNotSelfOrOwner(user, "error.team.cannotRemoveOwner");
        handleReassignment(user, req.reassignToUserId(), req.leaveUnassigned());

        user.setStatus(AppUser.Status.REMOVED);
        user.setDeletedAt(Instant.now());
        user.bumpTokenVersion();
        appUserRepository.save(user);
        refreshTokenService.revokeAllForUser(userId);
    }

    @Transactional
    public void resendInvite(UUID userId) {
        AppUser user = requireMember(userId);
        if (user.getStatus() != AppUser.Status.INVITED) {
            throw new BadRequestException("status", "NOT_INVITED", "error.team.notInvited");
        }
        sendInvite(user.getOrgId(), user, true);
    }

    @Transactional
    public void resetPassword(UUID userId) {
        AppUser user = requireMember(userId);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("email", "EMAIL_REQUIRED", "error.team.emailRequiredForReset");
        }
        String token = generateOpaqueToken();
        redis.opsForValue().set("reset:token:" + token, user.getId().toString(), RESET_TOKEN_TTL);
        outboxService.enqueueEmail(user.getOrgId(), "app_user", user.getId(), user.getEmail(), "Reset your Shardeya password",
                "Use this code to reset your password: " + token + "\nThis code expires in 30 minutes and can only be used once.");
    }

    public StaffActivityResponse activity(UUID userId) {
        AppUser user = requireMember(userId);
        UUID orgId = user.getOrgId();
        long leadsAssigned = customerRepository.count(
                org.springframework.data.jpa.domain.Specification
                        .where(com.shardeya.foundation.customer.CustomerSpecifications.orgId(orgId))
                        .and(com.shardeya.foundation.customer.CustomerSpecifications.notDeleted())
                        .and(com.shardeya.foundation.customer.CustomerSpecifications.assignedTo(userId)));
        long interactionsConducted = interactionRepository.findAll().stream()
                .filter(i -> i.getOrgId().equals(orgId) && userId.equals(i.getConductedBy())).count();
        return new StaffActivityResponse(leadsAssigned, interactionsConducted, user.getLastLoginAt());
    }

    // ------------------------------------------------------------- helpers

    private void handleReassignment(AppUser user, UUID reassignToUserId, boolean leaveUnassigned) {
        List<com.shardeya.foundation.customer.Customer> assigned = customerRepository.findAll(
                org.springframework.data.jpa.domain.Specification
                        .where(com.shardeya.foundation.customer.CustomerSpecifications.orgId(user.getOrgId()))
                        .and(com.shardeya.foundation.customer.CustomerSpecifications.notDeleted())
                        .and(com.shardeya.foundation.customer.CustomerSpecifications.assignedTo(user.getId())));
        if (assigned.isEmpty()) {
            return;
        }
        if (!leaveUnassigned && reassignToUserId == null) {
            throw new ConflictException("error.team.reassignmentRequired");
        }
        AppUser newAssignee = null;
        if (reassignToUserId != null) {
            newAssignee = appUserRepository.findByIdAndOrgIdAndDeletedAtIsNull(reassignToUserId, user.getOrgId())
                    .orElseThrow(() -> new BadRequestException("reassignToUserId", "ASSIGNEE_INVALID", "error.team.reassigneeInvalid"));
        }
        for (com.shardeya.foundation.customer.Customer customer : assigned) {
            customer.setAssignedTo(newAssignee == null ? null : newAssignee.getId());
            customerRepository.save(customer);
            // Same denormalised-assignee bug as CustomerService.assignInternal():
            // the FOLLOW_UP calendar event's own assignedTo column doesn't
            // follow this mutation on its own.
            customerService.syncFollowUpProjection(customer);
            customerService.syncSiteVisitProjection(customer);
        }
    }

    private void applyProjectAccess(UUID orgId, UUID userId, List<UUID> projectIds) {
        if (projectIds == null) {
            return;
        }
        for (UUID projectId : projectIds) {
            UserProjectAccess access = new UserProjectAccess(UUID.randomUUID(), orgId, userId, projectId);
            userProjectAccessRepository.save(access);
        }
    }

    private void sendInvite(UUID orgId, AppUser user, boolean isResend) {
        String token = generateOpaqueToken();
        String tokenHash = sha256(token);
        Instant expiresAt = Instant.now().plus(INVITE_TTL);

        StaffInvite invite = staffInviteRepository.findFirstByAppUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId())
                .filter(i -> i.getAcceptedAt() == null)
                .orElse(null);
        String channel = user.getEmail() != null && !user.getEmail().isBlank() ? "EMAIL" : "SMS";
        if (invite == null) {
            invite = new StaffInvite(UUID.randomUUID(), orgId, user.getId(), tokenHash, channel, expiresAt);
        } else {
            invite.setTokenHash(tokenHash);
            invite.setExpiresAt(expiresAt);
            if (isResend) {
                invite.setResendCount((short) (invite.getResendCount() + 1));
            }
        }
        invite.setSentAt(Instant.now());
        staffInviteRepository.save(invite);

        redis.opsForValue().set("invite:token:" + token, user.getId().toString(), INVITE_TTL);

        String link = frontendBaseUrl + "/accept-invite?token=" + token;
        String message = "You've been added to Shardeya. Set your password here: " + link;
        if ("EMAIL".equals(channel)) {
            outboxService.enqueueEmail(user.getOrgId(), "app_user", user.getId(), user.getEmail(), "You've been added to Shardeya", message);
        } else {
            // sendText, not sendOtp -- see SmsGateway's own javadoc for why
            // this is the one non-OTP SMS this project sends.
            smsGateway.sendText(user.getMobile(), message, "STAFF_INVITE");
        }
    }

    private AppUser requireMember(UUID userId) {
        UUID orgId = tenantContextBinder.currentOrgId();
        return appUserRepository.findByIdAndOrgIdAndDeletedAtIsNull(userId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
    }

    private void assertNotSelfOrOwner(AppUser user, String messageKey) {
        if (user.isOwner()) {
            throw new ForbiddenException(messageKey);
        }
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private TeamMemberResponse toResponse(AppUser user) {
        List<UUID> access = user.getProjectAccessMode() == AppUser.ProjectAccessMode.SCOPED
                ? userProjectAccessRepository.findByOrgIdAndUserId(user.getOrgId(), user.getId())
                        .stream().map(UserProjectAccess::getProjectId).toList()
                : List.of();
        StaffInvite invite = staffInviteRepository.findFirstByAppUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId()).orElse(null);
        String inviteStatus = invite == null ? null : (invite.getAcceptedAt() != null ? "ACCEPTED"
                : (invite.getExpiresAt().isBefore(Instant.now()) ? "EXPIRED" : "PENDING"));
        return new TeamMemberResponse(user.getId(), user.getFullName(), user.getMobile(), user.getEmail(),
                user.getRole().getCode(), user.getStatus().name(), user.isOwner(),
                user.getProjectAccessMode() == AppUser.ProjectAccessMode.ALL, access, user.getLastLoginAt(),
                user.getCreatedAt(), inviteStatus, invite == null ? null : invite.getSentAt());
    }
}
