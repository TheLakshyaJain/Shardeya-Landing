package com.shardeya.foundation.auth;

import com.shardeya.foundation.auth.dto.ChangePasswordRequest;
import com.shardeya.foundation.auth.dto.EntitlementsSummary;
import com.shardeya.foundation.auth.dto.MeResponse;
import com.shardeya.foundation.auth.dto.OrgSummary;
import com.shardeya.foundation.auth.dto.UpdateProfileRequest;
import com.shardeya.foundation.auth.dto.UserSummary;
import com.shardeya.foundation.rbac.Role;
import com.shardeya.foundation.subscription.SubscriptionRepository;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.platform.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Only ever runs against an already-bound {@link TenantContextBinder} —
 * every endpoint here requires authentication (TenantContextFilter has
 * already validated the JWT and bound context before these handlers run),
 * so unlike AuthService there's no chicken-and-egg tenant-context problem.
 */
@Service
public class UserService {

    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AuthLookupRepository authLookupRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final TenantContextBinder tenantContextBinder;

    public UserService(AppUserRepository appUserRepository, OrganizationRepository organizationRepository,
                        SubscriptionRepository subscriptionRepository, AuthLookupRepository authLookupRepository,
                        RefreshTokenService refreshTokenService, PasswordEncoder passwordEncoder,
                        TenantContextBinder tenantContextBinder) {
        this.appUserRepository = appUserRepository;
        this.organizationRepository = organizationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.authLookupRepository = authLookupRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.tenantContextBinder = tenantContextBinder;
    }

    @Transactional
    public MeResponse me() {
        var tenant = tenantContextBinder.current();
        AppUser user = appUserRepository.findById(tenant.userId())
                .orElseThrow(() -> new ResourceNotFoundException("error.auth.notFound"));
        Organization org = organizationRepository.findById(tenant.orgId())
                .orElseThrow(() -> new ResourceNotFoundException("error.auth.notFound"));
        String planCode = subscriptionRepository.findByOrgId(tenant.orgId())
                .map(s -> s.getPlanCode())
                .orElse("FREE");

        return new MeResponse(
                toUserSummary(user),
                new OrgSummary(org.getId(), org.getType().name(), org.getName(), org.getCity()),
                user.getRole().getPermissionCodes().stream().sorted().toList(),
                new EntitlementsSummary(planCode),
                0);
    }

    @Transactional
    public MeResponse updateProfile(UpdateProfileRequest request) {
        var tenant = tenantContextBinder.current();
        AppUser user = appUserRepository.findById(tenant.userId())
                .orElseThrow(() -> new ResourceNotFoundException("error.auth.notFound"));

        if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())
                && authLookupRepository.existsByEmail(request.email())) {
            throw new ConflictException("error.auth.emailRegistered");
        }

        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.language() != null) {
            user.setLanguage(request.language());
        }
        if (request.city() != null) {
            Organization org = organizationRepository.findById(tenant.orgId()).orElseThrow();
            org.setCity(request.city());
        }

        return me();
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        var tenant = tenantContextBinder.current();
        AppUser user = appUserRepository.findById(tenant.userId())
                .orElseThrow(() -> new ResourceNotFoundException("error.auth.notFound"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("error.auth.currentPasswordInvalid");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("newPassword", "PASSWORD_SAME_AS_OLD", "error.password.sameAsOld");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.bumpTokenVersion();
        // All sessions re-authenticate, including this one's refresh token —
        // its access token keeps working until its own 15-min expiry, same
        // as the forgot-password-reset path.
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private UserSummary toUserSummary(AppUser user) {
        Role role = user.getRole();
        return new UserSummary(
                user.getId(), user.getFullName(), user.getMobile(), user.getEmail(),
                role.getCode(), user.isOwner(), user.getLanguage());
    }
}
