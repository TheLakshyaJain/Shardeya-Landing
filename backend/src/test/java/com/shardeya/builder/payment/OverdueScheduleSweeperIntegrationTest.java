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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B-05 §7: "A nightly job flips PENDING/PARTIALLY_PAID to OVERDUE when
 * due_date &lt; today." Exercises {@link OverdueScheduleSweeper#sweepAllOrgs()}
 * directly rather than waiting for its 1am cron -- the method is written to
 * be callable either way for exactly this reason.
 */
class OverdueScheduleSweeperIntegrationTest extends AbstractIntegrationTest {

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
    private OverdueScheduleSweeper sweeper;
    @Autowired
    private OutboxPoller outboxPoller;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void sweepFlipsPastDueUnpaidSchedulesToOverdueAndNotifies() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Overdue Sweep Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Sweep Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", "9" + String.valueOf(System.nanoTime()).substring(0, 9))
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Overdue Sweep Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", BigDecimal.valueOf(1200), "SQ_FT",
                BigDecimal.valueOf(1200), BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);

        // A single instalment due yesterday, deliberately left unpaid.
        SaleResponse sale = plotSaleService.create(plotId, new SaleCreateRequest(null, "Rajesh Kumar", "9876543210", null,
                null, null, null, IndianTime.today(), BigDecimal.valueOf(4_200_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(4_200_000), IndianTime.today().minusDays(1))),
                null));

        List<PaymentSchedule> before = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        assertThat(before.get(0).getStatus()).isEqualTo(PaymentSchedule.Status.PENDING);

        // Sweep runs its own per-org tenant binding internally -- must NOT
        // run inside this test's own bound context (which is org-specific);
        // clear first so the sweep discovers every org itself, same as
        // production (an unbound caller, e.g. the @Scheduled cron trigger).
        TestTenantContext.clear();
        int swept = sweeper.sweepAllOrgs();
        assertThat(swept).isGreaterThanOrEqualTo(1);

        // The sweep only enqueues an outbox_event (CLAUDE.md rule #6: side
        // effects never happen inline) -- the actual notification row is
        // created later, asynchronously, by OutboxPoller.dispatchReady()
        // (normally on its own 2s @Scheduled tick). Driving it directly here
        // avoids a flaky sleep-and-poll wait in the test.
        outboxPoller.dispatchReady();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());
        List<PaymentSchedule> after = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        assertThat(after.get(0).getStatus()).isEqualTo(PaymentSchedule.Status.OVERDUE);

        assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10)))
                .anyMatch(n -> n.getTypeCode().equals("INSTALMENT_OVERDUE"));
    }

    // M-06 second half, §22.3 "Instalment overdue 3+ days -> day 3, then
    // weekly" -- distinct from the status-transition sweep above, which
    // only ever fires once (day 1).
    @Test
    void reminderSweepNotifiesAgainAtDayThreeButNotOnAnImmediateReRun() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Overdue Reminder Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Reminder Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", "9" + String.valueOf(System.nanoTime()).substring(0, 9))
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Overdue Reminder Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-2", BigDecimal.valueOf(1200), "SQ_FT",
                BigDecimal.valueOf(1200), BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);

        SaleResponse sale = plotSaleService.create(plotId, new SaleCreateRequest(null, "Priya Sharma", "9876500000", null,
                null, null, null, IndianTime.today(), BigDecimal.valueOf(4_200_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(4_200_000), IndianTime.today().minusDays(3))),
                null));

        // Simulate the day-1 status-transition sweep having already run
        // (this test targets the SEPARATE reminder cadence, day 3+).
        List<PaymentSchedule> rows = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        PaymentSchedule schedule = rows.get(0);
        schedule.setStatus(PaymentSchedule.Status.OVERDUE);
        scheduleRepository.saveAndFlush(schedule);

        TestTenantContext.clear();
        int firstRun = sweeper.remindOverdueAllOrgs();
        assertThat(firstRun).isGreaterThanOrEqualTo(1);

        // Immediate re-run (same day) must NOT double-remind.
        int secondRun = sweeper.remindOverdueAllOrgs();
        assertThat(secondRun).isEqualTo(0);

        // Backdating last_reminder_sent_at past the 7-day repeat window
        // must make the SAME row eligible again.
        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());
        PaymentSchedule reloaded = scheduleRepository.findByIdAndDeletedAtIsNull(schedule.getId()).orElseThrow();
        reloaded.setLastReminderSentAt(java.time.Instant.now().minusSeconds(8L * 24 * 3600));
        scheduleRepository.saveAndFlush(reloaded);
        TestTenantContext.clear();

        int thirdRun = sweeper.remindOverdueAllOrgs();
        assertThat(thirdRun).isGreaterThanOrEqualTo(1);
    }
}
