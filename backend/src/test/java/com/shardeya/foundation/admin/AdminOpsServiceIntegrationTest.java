package com.shardeya.foundation.admin;

import com.shardeya.foundation.admin.dto.AppErrorLogRow;
import com.shardeya.foundation.admin.dto.MessageDeliveryLogRow;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.foundation.notification.MessageDelivery;
import com.shardeya.foundation.notification.MessageDeliveryRepository;
import com.shardeya.platform.ForbiddenException;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M-14 stand-in (CLAUDE.md "Post-M7 -- Minimal Ops Visibility"). Covers
 * exactly what matters for a page an Admin uses to check "is anything
 * broken": tenant isolation on both new read paths (message deliveries,
 * error log), the platform-level (org_id IS NULL) error row being visible
 * to every org, and the SETTINGS_MANAGE permission gate actually being
 * enforced -- not just present as an unused annotation.
 */
class AdminOpsServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AdminOpsService service;
    @Autowired
    private AdminOpsController controller;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private MessageDeliveryRepository messageDeliveryRepository;
    @Autowired
    private AppErrorLogRepository errorLogRepository;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    private UUID seedOrg(String name) {
        UUID orgId = UUID.randomUUID();
        organizationRepository.saveAndFlush(new Organization(orgId, Organization.Type.BUILDER, name, "Jaipur"));
        return orgId;
    }

    @Test
    void messageDeliveriesAreScopedToTheCallersOwnOrg() {
        UUID orgA = seedOrg("Ops Test Org A");
        UUID orgB = seedOrg("Ops Test Org B");

        TestTenantContext.bind(orgA, UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of("SETTINGS_MANAGE"));
        messageDeliveryRepository.saveAndFlush(new MessageDelivery(UUID.randomUUID(), orgA, MessageDelivery.Channel.WHATSAPP,
                "*******1234", "INSTALMENT_DUE_TODAY", "StubWhatsAppGateway", null));

        TestTenantContext.bind(orgB, UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of("SETTINGS_MANAGE"));
        messageDeliveryRepository.saveAndFlush(new MessageDelivery(UUID.randomUUID(), orgB, MessageDelivery.Channel.SMS,
                "*******5678", "INSTALMENT_OVERDUE", "StubSmsGateway", null));

        TestTenantContext.bind(orgA, UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of("SETTINGS_MANAGE"));
        List<MessageDeliveryLogRow> rows = service.messageDeliveries(null, 50);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).recipientMasked()).isEqualTo("*******1234");
    }

    @Test
    void recentErrorsShowTheCallersOwnOrgAndPlatformLevelRowsButNeverAnotherOrgS() {
        UUID orgA = seedOrg("Ops Test Org A2");
        UUID orgB = seedOrg("Ops Test Org B2");

        TestTenantContext.bind(orgA, UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of("SETTINGS_MANAGE"));
        errorLogRepository.saveAndFlush(new AppErrorLog(UUID.randomUUID(), orgA, null, "GET", "/api/v1/projects",
                "java.lang.NullPointerException", "org A's own error"));
        // A platform-level error (org_id IS NULL) -- e.g. a failure before any
        // tenant context was ever bound -- must be visible from EVERY org's
        // admin view, same as role.org_id's own nullable-system-row shape.
        errorLogRepository.saveAndFlush(new AppErrorLog(UUID.randomUUID(), null, null, "POST", "/api/v1/auth/login",
                "java.lang.IllegalStateException", "platform-level error"));

        TestTenantContext.bind(orgB, UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of("SETTINGS_MANAGE"));
        errorLogRepository.saveAndFlush(new AppErrorLog(UUID.randomUUID(), orgB, null, "GET", "/api/v1/plots",
                "java.lang.RuntimeException", "org B's own error, must never leak to org A"));

        TestTenantContext.bind(orgA, UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of("SETTINGS_MANAGE"));
        List<AppErrorLogRow> rows = service.recentErrors(200);

        // Platform-level (org_id IS NULL) rows are, by design, visible to
        // EVERY org -- other test classes sharing this suite's one
        // Testcontainers Postgres may have already inserted their own, so
        // this asserts "contains at least", never "contains exactly",
        // for anything platform-level. Org isolation itself (the actual
        // thing this test exists to prove) is still an exact, hard "never".
        assertThat(rows).extracting(AppErrorLogRow::message)
                .contains("org A's own error", "platform-level error")
                .doesNotContain("org B's own error, must never leak to org A");
    }

    @Test
    void bothEndpointsRejectACallerWithoutSettingsManage() {
        UUID orgId = seedOrg("Ops Test Org NoPerm");
        TestTenantContext.bind(orgId, UUID.randomUUID(), "BUILDER", "BUILDER_MANAGER", Set.of("DATA_VIEW_ALL"));

        assertThatThrownBy(() -> controller.messageDeliveries(null, 50)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.errors(50)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void bothEndpointsSucceedForACallerWithSettingsManage() {
        UUID orgId = seedOrg("Ops Test Org WithPerm");
        TestTenantContext.bind(orgId, UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of("SETTINGS_MANAGE"));

        // messageDeliveries is genuinely empty for this brand-new org --
        // message_delivery has no platform-level/null-org concept at all
        // (org_id is NOT NULL), so there is nothing else that could leak in.
        assertThat(controller.messageDeliveries(null, 50)).isEmpty();
        // errors() may legitimately be non-empty: platform-level (org_id IS
        // NULL) rows from other test classes sharing this suite's one
        // Testcontainers Postgres are correctly visible here too (see
        // recentErrorsShowTheCallersOwnOrgAndPlatformLevelRowsButNeverAnotherOrgS
        // for the actual isolation proof) -- this assertion is purely "the
        // permission gate let the call through and it didn't throw".
        assertThat(controller.errors(50)).isNotNull();
    }
}
