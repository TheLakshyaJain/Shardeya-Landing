package com.shardeya.builder.payment;

import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleService;
import com.shardeya.builder.sale.dto.SaleCreateRequest;
import com.shardeya.builder.sale.dto.SaleResponse;
import com.shardeya.builder.sale.dto.ScheduleRowRequest;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M-06 §22.3, "Instalment due today" -- In-app + WhatsApp, 09:00 IST.
 * Exercises {@link InstalmentDueTodayScheduler#sweepAllOrgs()} directly
 * rather than waiting for its own cron -- same "no HTTP trigger for a
 * @Scheduled job" reasoning {@link OverdueScheduleSweeperIntegrationTest}
 * and {@code FollowUpDueSweeperIntegrationTest} already established.
 */
class InstalmentDueTodaySchedulerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlotSaleService plotSaleService;
    @Autowired
    private PlotRepository plotRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private PaymentScheduleRepository scheduleRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private WhatsAppOptinRepository whatsAppOptinRepository;
    @Autowired
    private InstalmentDueTodayScheduler scheduler;
    @Autowired
    private OutboxPoller outboxPoller;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void sweepNotifiesInAppAndWhatsAppForAnInstalmentDueToday() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        String mobile = "9" + String.valueOf(System.nanoTime()).substring(0, 9);

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Due-Today Sweep Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Due Today Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", mobile)
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Due-Today Sweep Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", BigDecimal.valueOf(1200), "SQ_FT",
                BigDecimal.valueOf(1200), BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);

        // Same opt-in precondition NotificationDispatchService now enforces
        // for every WhatsApp send -- see FollowUpDueSweeperIntegrationTest's
        // own comment for why this is required post-M-06-second-half.
        whatsAppOptinRepository.saveAndFlush(new WhatsAppOptin(UUID.randomUUID(), orgId, mobile, "SELF_SERVICE"));

        SaleResponse sale = plotSaleService.create(plotId, new SaleCreateRequest(null, "Rajesh Kumar", "9876543210", null,
                null, null, null, IndianTime.today(), BigDecimal.valueOf(4_200_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(4_200_000), IndianTime.today())),
                null));

        List<PaymentSchedule> before = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        assertThat(before.get(0).getStatus()).isEqualTo(PaymentSchedule.Status.PENDING);
        assertThat(before.get(0).getDueDate()).isEqualTo(IndianTime.today());

        TestTenantContext.clear();
        int swept = scheduler.sweepAllOrgs();
        assertThat(swept).isGreaterThanOrEqualTo(1);

        // Dispatching the NOTIFICATION event is what makes
        // NotificationDispatchService actually decide on WhatsApp and
        // enqueue a second, WHATSAPP outbox event -- see
        // FollowUpDueSweeperIntegrationTest's own comment for the exact
        // same two-step shape.
        outboxPoller.dispatchReady();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());
        assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10)))
                .anyMatch(n -> n.getTypeCode().equals("INSTALMENT_DUE_TODAY"));

        assertThat(outboxEventRepository.findAll())
                .anyMatch(e -> e.getEventType().equals("WHATSAPP") && e.getPayload().contains(mobile)
                        && e.getPayload().contains("Rajesh Kumar") && e.getPayload().contains("A-1"));

        // last_reminder_sent_at is now stamped, so a same-day re-run doesn't
        // double-notify (this scheduler's own documented dedup approximation).
        int reswept = scheduler.sweepAllOrgs();
        assertThat(reswept).isEqualTo(0);
    }
}
