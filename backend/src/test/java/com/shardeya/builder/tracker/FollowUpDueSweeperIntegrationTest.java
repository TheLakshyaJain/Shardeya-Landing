package com.shardeya.builder.tracker;

import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.foundation.customer.Customer;
import com.shardeya.foundation.customer.CustomerRepository;
import com.shardeya.foundation.notification.NotificationRepository;
import com.shardeya.foundation.notification.WhatsAppOptin;
import com.shardeya.foundation.notification.WhatsAppOptinRepository;
import com.shardeya.platform.OutboxEventRepository;
import com.shardeya.platform.OutboxPoller;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.shared.IndianTime;
import com.shardeya.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B-13 §12: "Follow-up due today -> in-app at 09:00 to the assignee."
 * Exercises {@link FollowUpDueSweeper#sweepAllOrgs()} directly rather than
 * waiting for its 09:00 IST cron -- same pattern
 * OverdueScheduleSweeperIntegrationTest already established for the
 * identically-shaped M3 sweeper. This is also the regression test for the
 * M4 gap this milestone closed (FOLLOWUP_DUE was seeded since M4 but
 * nothing ever fired it).
 *
 * <p><b>Updated for the WhatsApp notification overhaul:</b> FOLLOWUP_DUE
 * originally shipped with WhatsApp as a default channel (see this class's
 * git history for that version of this test); a later product decision
 * removed WhatsApp from this type entirely, in-app only from now on. The
 * test below is now the regression guard for the REMOVAL, not the
 * original addition -- it deliberately seeds a real, active opt-in for
 * the assignee's mobile and still asserts NO WhatsApp event is produced,
 * proving the channel is gone at the dispatch layer itself
 * (NotificationDispatchService's own WHATSAPP_ELIGIBLE_TYPES allowlist),
 * not merely defaulted-off and one preference-toggle away from coming back.
 */
class FollowUpDueSweeperIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private FollowUpDueSweeper sweeper;
    @Autowired
    private OutboxPoller outboxPoller;
    @Autowired
    private WhatsAppOptinRepository whatsAppOptinRepository;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void sweepNotifiesAssigneeInAppOnlyNeverWhatsAppForFollowUpsDueToday() {
        UUID orgId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String assigneeMobile = "9" + String.valueOf(System.nanoTime()).substring(0, 9);

        TestTenantContext.bind(orgId, assigneeId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Follow-up Sweep Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Follow-up Assignee', :mobile, :roleId, true)")
                    .setParameter("id", assigneeId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", assigneeMobile)
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Customer customer = new Customer(customerId, orgId, "Priya Sharma", "9123456789",
                BigDecimal.valueOf(500000), BigDecimal.valueOf(1000000), Customer.Source.WALK_IN);
        customer.setAssignedTo(assigneeId);
        customer.setFollowUpDate(IndianTime.today());
        customerRepository.saveAndFlush(customer);

        // Deliberately seeded even though FOLLOWUP_DUE no longer has a
        // WhatsApp path at all -- an active opt-in here proves the missing
        // WhatsApp event below is because the TYPE is excluded (the
        // allowlist gate), not merely because this recipient happened to
        // be unreachable.
        whatsAppOptinRepository.saveAndFlush(new WhatsAppOptin(UUID.randomUUID(), orgId, assigneeMobile, "SELF_SERVICE"));

        // Sweep runs its own per-org tenant binding internally -- must NOT
        // run inside this test's own bound context, same reasoning
        // OverdueScheduleSweeperIntegrationTest already documents.
        TestTenantContext.clear();
        int swept = sweeper.sweepAllOrgs();
        assertThat(swept).isGreaterThanOrEqualTo(1);

        // The sweep only enqueues a NOTIFICATION outbox event (CLAUDE.md
        // rule #6) -- dispatchReady() is what turns that into the real
        // in-app row (and, for an eligible type, a second-order WHATSAPP
        // outbox insert). FOLLOWUP_DUE is no longer WhatsApp-eligible at
        // all, so this call should produce the in-app notification and
        // nothing else.
        outboxPoller.dispatchReady();

        TestTenantContext.bind(orgId, assigneeId, "BUILDER", "BUILDER_ADMIN", Set.of());
        assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(assigneeId, PageRequest.of(0, 10)))
                .anyMatch(n -> n.getTypeCode().equals("FOLLOWUP_DUE"));

        // The regression guard itself: no WHATSAPP outbox event exists for
        // this org at all, despite a real, active opt-in existing for the
        // assignee's mobile -- WhatsApp is gone for this type at the
        // dispatch layer, not just un-defaulted.
        assertThat(outboxEventRepository.findAll())
                .noneMatch(e -> e.getEventType().equals("WHATSAPP") && e.getPayload().contains(assigneeMobile));
    }
}
