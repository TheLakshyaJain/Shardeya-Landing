package com.shardeya.platform;

import com.shardeya.foundation.admin.AppErrorLog;
import com.shardeya.foundation.admin.AppErrorLogRepository;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M-14 stand-in (CLAUDE.md "Post-M7 -- Minimal Ops Visibility"): the catch-all
 * @ExceptionHandler(Exception.class) must actually persist an app_error_log
 * row, not just log a line -- this is what AdminOpsController's /admin/errors
 * endpoint reads from. Uses a real Spring MockHttpServletRequest (not a
 * mocking framework -- this codebase has none) so
 * GlobalExceptionHandler.handleUnexpected() runs exactly as it would for a
 * real request, only the transport layer is a lightweight test double.
 */
class GlobalExceptionHandlerErrorLogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GlobalExceptionHandler handler;
    @Autowired
    private AppErrorLogRepository errorLogRepository;
    @Autowired
    private OrganizationRepository organizationRepository;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void unhandledExceptionWithABoundTenantPersistsAnOrgScopedErrorLogRow() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        organizationRepository.saveAndFlush(new Organization(orgId, Organization.Type.BUILDER, "Error Log Test Org", "Jaipur"));
        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/boom");
        handler.handleUnexpected(new IllegalStateException("deliberate test failure, boom"), request);

        List<AppErrorLog> rows = errorLogRepository.findVisibleToOrg(orgId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(rows).anyMatch(r -> r.getOrgId().equals(orgId) && r.getUserId().equals(userId)
                && r.getHttpMethod().equals("GET") && r.getPath().equals("/api/v1/test/boom")
                && r.getExceptionClass().equals("java.lang.IllegalStateException")
                && r.getMessage().contains("deliberate test failure"));
    }

    @Test
    void unhandledExceptionWithNoTenantBoundStillPersistsAPlatformLevelRow() {
        TestTenantContext.clear();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        // Must not throw just because no tenant context is bound -- this is
        // exactly the pre-login-failure scenario this table exists for.
        handler.handleUnexpected(new RuntimeException("pre-auth boom"), request);

        // No org bound to read back through -- confirm directly via the
        // repository's own JpaRepository#findAll (still RLS-scoped, but a
        // NULL org_id row is visible under any/no bound context per the
        // policy's own "org_id IS NULL" clause).
        List<AppErrorLog> all = errorLogRepository.findAll();
        assertThat(all).anyMatch(r -> r.getOrgId() == null && r.getPath().equals("/api/v1/auth/login")
                && r.getMessage().contains("pre-auth boom"));
    }
}
