package com.shardeya.foundation.auth;

import com.shardeya.foundation.rbac.Role;
import com.shardeya.foundation.rbac.RoleRepository;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.platform.UnauthorizedException;
import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No {@code @Transactional} here on purpose: Spring's test-managed transaction
 * checks out its connection (and so applies the RLS GUC) in a hook that runs
 * BEFORE the test body, which is before {@link TestTenantContext#bind} has
 * had a chance to run — the insert then fails RLS's WITH CHECK even though
 * the context looks right by the time you'd read the code. Each repository
 * call here gets its own fresh connection checkout instead, the same as a
 * real request would.
 */
class RefreshTokenServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RoleRepository roleRepository;

    @AfterEach
    void clearContext() {
        TestTenantContext.clear();
    }

    private String uniqueMobile() {
        return "9" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
    }

    private AppUser createTestUser() {
        Organization org = new Organization(UUID.randomUUID(), Organization.Type.BUILDER, "RT Test Co", "Pune");
        Role role = roleRepository.findByCodeAndOrgIdIsNull("BUILDER_ADMIN").orElseThrow();

        TestTenantContext.bind(org.getId(), UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of());
        organizationRepository.saveAndFlush(org);
        AppUser user = new AppUser(UUID.randomUUID(), org.getId(), "RT Test User", uniqueMobile(), role, true);
        appUserRepository.saveAndFlush(user);
        return user;
    }

    @Test
    void rotateIssuesANewTokenAndRevokesTheOld() {
        AppUser user = createTestUser();
        RefreshTokenService.IssueResult issued = refreshTokenService.issue(user.getId(), false, "junit", "127.0.0.1");

        RefreshTokenService.IssueResult rotated = refreshTokenService.rotate(issued.rawToken(), "junit", "127.0.0.1");

        assertThat(rotated.rawToken()).isNotEqualTo(issued.rawToken());
    }

    @Test
    void reusingAnAlreadyRotatedTokenRevokesTheWholeFamilyAndBothTokensStopWorking() {
        AppUser user = createTestUser();
        RefreshTokenService.IssueResult first = refreshTokenService.issue(user.getId(), false, "junit", "127.0.0.1");
        RefreshTokenService.IssueResult second = refreshTokenService.rotate(first.rawToken(), "junit", "127.0.0.1");

        // Presenting the already-replaced first token is the reuse signature.
        assertThatThrownBy(() -> refreshTokenService.rotate(first.rawToken(), "junit", "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class);

        // The legitimate, never-reused second token must ALSO be dead now —
        // that's the actual point of family-wide revocation (stolen-token
        // containment forces re-login even for the honest holder).
        assertThatThrownBy(() -> refreshTokenService.rotate(second.rawToken(), "junit", "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void revokeAllForUserKillsEveryActiveSession() {
        AppUser user = createTestUser();
        RefreshTokenService.IssueResult session1 = refreshTokenService.issue(user.getId(), false, "junit", "127.0.0.1");
        RefreshTokenService.IssueResult session2 = refreshTokenService.issue(user.getId(), true, "junit", "10.0.0.1");

        refreshTokenService.revokeAllForUser(user.getId());

        assertThatThrownBy(() -> refreshTokenService.rotate(session1.rawToken(), "junit", "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> refreshTokenService.rotate(session2.rawToken(), "junit", "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
