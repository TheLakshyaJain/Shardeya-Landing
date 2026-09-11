package com.shardeya.builder.financial;

import com.shardeya.builder.financial.dto.FinancialPaymentRow;
import com.shardeya.builder.payment.PaymentRecord;
import com.shardeya.builder.payment.dto.ChequeStatusRequest;
import com.shardeya.builder.payment.dto.PaymentCreateRequest;
import com.shardeya.builder.payment.dto.ReverseRequest;
import com.shardeya.builder.payment.PaymentService;
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
import com.shardeya.platform.TestTenantContext;
import com.shardeya.shared.IndianTime;
import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the real M5 bug found during manual verification:
 * FinancialService's overdue figures (summary()'s overdueInstalments AND
 * pending()'s overdueOnly filter) originally gated on
 * payment_schedule.status='OVERDUE', which is only refreshed once daily by
 * OverdueScheduleSweeper (M3, 9am IST cron) -- so a schedule that crossed
 * its due date since the last run stayed invisible here for up to 24h, even
 * though Tracker's own Collection tab (TrackerService.applyRange's
 * "overdue" case) already computed the same fact live off due_date and
 * caught it immediately. Fixed by making both FinancialService queries
 * compute overdue live off due_date too, matching Tracker and Dashboard's
 * alert (also fixed alongside this).
 */
class FinancialServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FinancialService financialService;
    @Autowired
    private PlotSaleService plotSaleService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PlotRepository plotRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void overdueFiguresAreComputedLiveOffDueDateNotTheOnceDailySweptStatusColumn() {
        UUID plotId = seedOrgProjectAndPlot();
        LocalDate today = IndianTime.today();

        // A schedule row whose due_date is genuinely in the past, but whose
        // status column is still whatever PlotSaleService.create() left it
        // as (PENDING) -- exactly the state a real schedule sits in for up
        // to 24h after crossing its due date, before the next 9am sweep
        // ever touches it. No sweep is invoked anywhere in this test.
        SaleResponse sale = plotSaleService.create(plotId, new SaleCreateRequest(null, "Vikram Joshi", "9876543211",
                null, null, null, null, today, BigDecimal.valueOf(3_750_000), null, null, null, null,
                PlotSale.PaymentType.INSTALMENT,
                List.of(new ScheduleRowRequest("Overdue instalment", BigDecimal.valueOf(500_000), today.minusDays(10)),
                        new ScheduleRowRequest("Future instalment", BigDecimal.valueOf(3_250_000), today.plusMonths(3))),
                null));

        var summary = financialService.summary(null, null, null);
        assertThat(summary.overdueInstalments().count()).isEqualTo(1);
        assertThat(summary.overdueInstalments().amount()).isEqualByComparingTo(BigDecimal.valueOf(500_000));

        var overdueOnly = financialService.pending(null, true, null, 10);
        assertThat(overdueOnly.items()).hasSize(1);
        assertThat(overdueOnly.items().get(0).plotSaleId()).isEqualTo(sale.id());

        // 03-BUILDER-MODULES.md B-08 §7: "Pending vs Overdue are distinct:
        // pending = not yet due; overdue = past due date and unpaid" --
        // pending(overdueOnly=false) must NOT also include the overdue row
        // (a real bug found on a live account: the same instalment showed
        // up in both the Pending and Overdue sections of the Financials
        // page because this call never excluded due_date < today).
        var pendingOnly = financialService.pending(null, false, null, 10);
        assertThat(pendingOnly.items()).hasSize(1);
        assertThat(pendingOnly.items().get(0).dueDate()).isEqualTo(today.plusMonths(3));

        // Paying the overdue row off in full must remove it from both views
        // immediately -- still no sweep involved.
        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(500_000), today,
                PaymentRecord.Mode.CASH, null, null, null));
        var afterPay = financialService.summary(null, null, null);
        assertThat(afterPay.overdueInstalments().count()).isEqualTo(0);
    }

    @Test
    void summaryWithAProjectFilterDoesNotThrow() {
        // Real bug found on a live account: the overdue-instalments query
        // scoped project filtering via a nonexistent "ps.project_id"
        // column (payment_schedule has no project_id of its own, only
        // plot_sale_id -- unlike payment_record/plot_sale, which both do).
        // This only ever threw when a caller actually selected a specific
        // project, which the org-wide (projectId=null) test above never
        // exercises -- exactly why it went unnoticed until a real user
        // used the project filter dropdown.
        UUID plotId = seedOrgProjectAndPlot();
        LocalDate today = IndianTime.today();
        SaleResponse sale = plotSaleService.create(plotId, new SaleCreateRequest(null, "Test Buyer", "9876543212",
                null, null, null, null, today, BigDecimal.valueOf(3_750_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(3_750_000), today)), null));

        var summary = financialService.summary(sale.projectId(), null, null);
        assertThat(summary.totalRevenueAllTime()).isEqualByComparingTo(BigDecimal.ZERO);

        var pendingForProject = financialService.pending(sale.projectId(), true, null, 10);
        assertThat(pendingForProject.items()).isEmpty();
    }

    @Test
    void paymentsFilteredByModeDoesNotThrow() {
        // Real bug found on a live account: payment_record.mode is a
        // Postgres enum (payment_mode); a bound PreparedStatement parameter
        // arrives typed as varchar, and Postgres has no
        // "payment_mode = varchar" operator without an explicit cast --
        // threw "operator does not exist: payment_mode = character varying"
        // as soon as a caller actually filtered by mode, same as every
        // other test in this class never doing so.
        UUID plotId = seedOrgProjectAndPlot();
        LocalDate today = IndianTime.today();
        SaleResponse sale = plotSaleService.create(plotId, new SaleCreateRequest(null, "Mode Filter Buyer", "9876543214",
                null, null, null, null, today, BigDecimal.valueOf(3_750_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(3_750_000), today)), null));
        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(3_750_000), today,
                PaymentRecord.Mode.UPI, "UTR-MODE-TEST", null, null));

        var upiOnly = financialService.payments(null, null, null, "UPI", null, 10);
        assertThat(upiOnly.items()).hasSize(1);

        var cashOnly = financialService.payments(null, null, null, "CASH", null, 10);
        assertThat(cashOnly.items()).isEmpty();
    }

    @Test
    void paymentsMarksAChequeBounceReversalDistinctlyFromAPlainOne() {
        // Real user-reported issue: the Financials Payments tab labelled a
        // cheque-bounce reversal with the same generic "Reversal" badge as
        // any other correction, with nothing on screen saying it was a
        // bounce specifically -- even though PaymentService.updateChequeStatus
        // already writes a real "cheque ... bounced" remark, this table
        // never surfaced it. dueToChequeBounce is the query-driven signal
        // the frontend badge now switches on instead of guessing from text.
        UUID plotId = seedOrgProjectAndPlot();
        LocalDate today = IndianTime.today();
        SaleResponse sale = plotSaleService.create(plotId, new SaleCreateRequest(null, "Bounce Test Buyer", "9876543215",
                null, null, null, null, today, BigDecimal.valueOf(3_750_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(3_750_000), today)), null));
        var chequePayment = paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(3_750_000), today,
                PaymentRecord.Mode.CHEQUE, "CHQ-001", null, null));
        paymentService.updateChequeStatus(chequePayment.id(), new ChequeStatusRequest(PaymentRecord.ChequeStatus.BOUNCED));

        var rows = financialService.payments(null, null, null, null, null, 10).items();
        assertThat(rows).hasSize(2);
        var reversalRow = rows.stream().filter(FinancialPaymentRow::isReversal).findFirst().orElseThrow();
        assertThat(reversalRow.dueToChequeBounce()).isTrue();
        assertThat(reversalRow.remarks()).contains("bounced");
        var originalRow = rows.stream().filter(r -> !r.isReversal()).findFirst().orElseThrow();
        assertThat(originalRow.dueToChequeBounce()).isFalse();
    }

    @Test
    void paymentsDoesNotMarkAManualReversalAsAChequeBounce() {
        UUID plotId = seedOrgProjectAndPlot();
        LocalDate today = IndianTime.today();
        SaleResponse sale = plotSaleService.create(plotId, new SaleCreateRequest(null, "Manual Reverse Buyer", "9876543216",
                null, null, null, null, today, BigDecimal.valueOf(3_750_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(3_750_000), today)), null));
        var cashPayment = paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(3_750_000), today,
                PaymentRecord.Mode.CASH, null, null, null));
        paymentService.reverse(cashPayment.id(), new ReverseRequest("Entered by mistake"));

        var rows = financialService.payments(null, null, null, null, null, 10).items();
        var reversalRow = rows.stream().filter(FinancialPaymentRow::isReversal).findFirst().orElseThrow();
        assertThat(reversalRow.dueToChequeBounce()).isFalse();
    }

    // Real bug found on a live account: revenueTrend() never accepted a
    // projectId at all, so selecting a project in FinancialFilterBar
    // scoped the summary cards correctly but left the trend chart showing
    // the org-wide total underneath -- despite B-08 §14.4's own "cascades
    // to every card and table on the page" contract for this filter. Two
    // projects, one payment each, prove the scoped call excludes the
    // other project's revenue while the unscoped call includes both.
    @Test
    void revenueTrendRespectsTheProjectFilterInsteadOfAlwaysBeingOrgWide() {
        UUID plot1Id = seedOrgProjectAndPlot();
        UUID orgId = lastSeedOrgId;
        UUID project2Id = UUID.randomUUID();
        UUID plot2Id = UUID.randomUUID();

        Project project2 = new Project(project2Id, orgId, "Financial Test Project 2", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "2 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project2);
        Plot plot2 = new Plot(plot2Id, orgId, project2Id, "B-1", BigDecimal.valueOf(1200), "SQ_FT",
                BigDecimal.valueOf(1200), BigDecimal.valueOf(1_000_000));
        plotRepository.saveAndFlush(plot2);

        LocalDate today = IndianTime.today();
        SaleResponse sale1 = plotSaleService.create(plot1Id, new SaleCreateRequest(null, "Project One Buyer", "9876543217",
                null, null, null, null, today, BigDecimal.valueOf(500_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(500_000), today)), null));
        paymentService.record(sale1.id(), new PaymentCreateRequest(BigDecimal.valueOf(500_000), today,
                PaymentRecord.Mode.CASH, null, null, null));

        SaleResponse sale2 = plotSaleService.create(plot2Id, new SaleCreateRequest(null, "Project Two Buyer", "9876543218",
                null, null, null, null, today, BigDecimal.valueOf(300_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(300_000), today)), null));
        paymentService.record(sale2.id(), new PaymentCreateRequest(BigDecimal.valueOf(300_000), today,
                PaymentRecord.Mode.CASH, null, null, null));

        BigDecimal project1Total = financialService.revenueTrend(sale1.projectId(), 1).stream()
                .map(com.shardeya.builder.financial.dto.RevenueTrendPoint::collected).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(project1Total).isEqualByComparingTo(BigDecimal.valueOf(500_000));

        BigDecimal orgWideTotal = financialService.revenueTrend(null, 1).stream()
                .map(com.shardeya.builder.financial.dto.RevenueTrendPoint::collected).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(orgWideTotal).isEqualByComparingTo(BigDecimal.valueOf(800_000));
    }

    // Set by seedOrgProjectAndPlot() -- exists purely so a test that needs a
    // SECOND project/plot in the SAME org (revenueTrendRespectsTheProjectFilter...
    // above) doesn't have to change this helper's return type, which every
    // other test in this class already depends on being a bare plotId.
    private UUID lastSeedOrgId;

    private UUID seedOrgProjectAndPlot() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        lastSeedOrgId = orgId;

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of("FINANCIAL_VIEW"));

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Financial Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Financial Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", "9" + String.valueOf(System.nanoTime()).substring(0, 9))
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Financial Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", BigDecimal.valueOf(1200), "SQ_FT",
                BigDecimal.valueOf(1200), BigDecimal.valueOf(3_750_000));
        plotRepository.saveAndFlush(plot);

        return plotId;
    }
}
