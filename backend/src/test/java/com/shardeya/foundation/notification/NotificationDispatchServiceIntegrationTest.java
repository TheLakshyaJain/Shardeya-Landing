package com.shardeya.foundation.notification;

import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.foundation.notification.dto.NotificationPreferenceUpdateRequest;
import com.shardeya.platform.OutboxEvent;
import com.shardeya.platform.OutboxEventRepository;
import com.shardeya.platform.OutboxPoller;
import com.shardeya.platform.OutboxService;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.support.AbstractIntegrationTest;
import com.shardeya.support.TestMobiles;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the WhatsApp notification overhaul's
 * recipient/channel-narrowing rules (see CLAUDE.md's own writeup for the
 * full "why"): WhatsApp is now a hard allowlist of exactly three types
 * (INSTALMENT_DUE_TODAY, INSTALMENT_OVERDUE, COMMISSION_DUE), and even for
 * those, only ever reaches the org OWNER -- every other user still gets
 * the in-app copy, just never WhatsApp/SMS for these events.
 *
 * <p>Drives the real outbox -> {@link OutboxPoller#dispatchReady()} ->
 * {@link NotificationDispatchService} path rather than calling
 * {@code dispatch()} directly, matching this project's own established
 * "exercise the real pipe, not the internals" convention (see
 * InstalmentDueTodaySchedulerIntegrationTest/FollowUpDueSweeperIntegrationTest).
 */
class NotificationDispatchServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OutboxService outboxService;
    @Autowired
    private OutboxPoller outboxPoller;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private WhatsAppOptinRepository whatsAppOptinRepository;
    @Autowired
    private NotificationPreferenceService preferenceService;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID orgId;
    private UUID ownerId;
    private UUID nonOwnerId;
    private String ownerMobile;
    private String nonOwnerMobile;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    private void seedOrgWithOwnerAndNonOwner() {
        orgId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        nonOwnerId = UUID.randomUUID();
        ownerMobile = TestMobiles.next();
        nonOwnerMobile = TestMobiles.next();

        TestTenantContext.bind(orgId, ownerId, "BUILDER", "BUILDER_ADMIN", Set.of());
        organizationRepository.saveAndFlush(new Organization(orgId, Organization.Type.BUILDER, "Dispatch Owner-Only Test Org", "Jaipur"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Owner User', :mobile, :roleId, true)")
                    .setParameter("id", ownerId).setParameter("orgId", orgId)
                    .setParameter("mobile", ownerMobile).setParameter("roleId", roleId)
                    .executeUpdate();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Non-Owner User', :mobile, :roleId, false)")
                    .setParameter("id", nonOwnerId).setParameter("orgId", orgId)
                    .setParameter("mobile", nonOwnerMobile).setParameter("roleId", roleId)
                    .executeUpdate();
        });

        // Both opted in -- the test is specifically about the ALLOWLIST +
        // OWNER-ONLY gate, not about opt-in status, so opt-in itself must
        // not be the reason a send is (or isn't) blocked.
        whatsAppOptinRepository.saveAndFlush(new WhatsAppOptin(UUID.randomUUID(), orgId, ownerMobile, "SELF_SERVICE"));
        whatsAppOptinRepository.saveAndFlush(new WhatsAppOptin(UUID.randomUUID(), orgId, nonOwnerMobile, "SELF_SERVICE"));
    }

    @Test
    void instalmentDueTodayReachesOnlyTheOwnerOverWhatsAppNotTheNonOwner() {
        seedOrgWithOwnerAndNonOwner();
        UUID entityId = UUID.randomUUID();

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                outboxService.enqueueNotification(orgId, "INSTALMENT_DUE_TODAY", "notification.instalmentDueToday", null,
                        Map.of("buyerName", "Test Buyer", "amount", "1000", "plotNumber", "A-1"), "payment_schedule", entityId));

        TestTenantContext.clear();
        outboxPoller.dispatchReady();

        TestTenantContext.bind(orgId, ownerId, "BUILDER", "BUILDER_ADMIN", Set.of());
        assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(ownerId, PageRequest.of(0, 10)))
                .as("owner still gets the in-app copy").anyMatch(n -> n.getTypeCode().equals("INSTALMENT_DUE_TODAY"));
        assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(nonOwnerId, PageRequest.of(0, 10)))
                .as("non-owner still gets the in-app copy too -- only WhatsApp narrows")
                .anyMatch(n -> n.getTypeCode().equals("INSTALMENT_DUE_TODAY"));

        // outbox_event has no org_id column at all (a cross-tenant queue
        // table, not RLS-scoped) -- findAll() returns every event the whole
        // test suite has ever created, so this-test's-own-mobile is the
        // only safe discriminator, same convention every other scheduler
        // test in this codebase already uses.
        List<OutboxEvent> whatsappEvents = outboxEventRepository.findAll().stream()
                .filter(e -> e.getEventType().equals("WHATSAPP")).toList();
        assertThat(whatsappEvents).as("owner reached over WhatsApp").anyMatch(e -> e.getPayload().contains(ownerMobile));
        assertThat(whatsappEvents).as("non-owner never reached over WhatsApp").noneMatch(e -> e.getPayload().contains(nonOwnerMobile));
    }

    @Test
    void commissionDueNowReachesTheOwnerOverWhatsAppTooNotJustInAppAndEmail() {
        seedOrgWithOwnerAndNonOwner();
        UUID entityId = UUID.randomUUID();

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                outboxService.enqueueNotification(orgId, "COMMISSION_DUE", "notification.commissionDue", null,
                        Map.of("brokerName", "Test Broker", "amount", "5000", "plotNumber", "A-1"), "commission_ledger_entry", entityId));

        TestTenantContext.clear();
        outboxPoller.dispatchReady();

        TestTenantContext.bind(orgId, ownerId, "BUILDER", "BUILDER_ADMIN", Set.of());
        List<OutboxEvent> whatsappEvents = outboxEventRepository.findAll().stream()
                .filter(e -> e.getEventType().equals("WHATSAPP")).toList();
        assertThat(whatsappEvents).as("COMMISSION_DUE now reaches WhatsApp, owner only")
                .anyMatch(e -> e.getPayload().contains(ownerMobile));
        assertThat(whatsappEvents).noneMatch(e -> e.getPayload().contains(nonOwnerMobile));
    }

    @Test
    void followUpDueNeverSendsWhatsAppEvenIfTheOwnerExplicitlyTogglesItOnInPreferences() {
        seedOrgWithOwnerAndNonOwner();
        UUID entityId = UUID.randomUUID();

        // The allowlist gate must win even against an explicit, deliberate
        // per-user preference override -- proving this is genuinely removed
        // at the dispatch layer, not just left off by default.
        TestTenantContext.bind(orgId, ownerId, "BUILDER", "BUILDER_ADMIN", Set.of());
        preferenceService.update(List.of(new NotificationPreferenceUpdateRequest("FOLLOWUP_DUE", true, true, false, false)));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                outboxService.enqueueNotificationForUser(orgId, ownerId, "FOLLOWUP_DUE", "notification.followUpDue", null,
                        Map.of("name", "Test Lead"), "customer", entityId));

        TestTenantContext.clear();
        outboxPoller.dispatchReady();

        assertThat(outboxEventRepository.findAll())
                .as("no WHATSAPP event reaching the owner's mobile, despite an explicit whatsapp=true preference")
                .noneMatch(e -> e.getEventType().equals("WHATSAPP") && e.getPayload().contains(ownerMobile));
    }
}
