package com.shardeya.builder.stats;

import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleService;
import com.shardeya.builder.sale.dto.SaleCreateRequest;
import com.shardeya.builder.sale.dto.ScheduleRowRequest;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.shared.IndianTime;
import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real bug, found on a live account: {@link StatsRefreshJob} originally
 * injected the plain, RLS-enforced shardeya_app JdbcTemplate. REFRESH
 * MATERIALIZED VIEW re-executes each view's defining query against its
 * FORCE-RLS-protected source tables (plot_sale, customer, interaction,
 * payment_record, broker_partner) -- with no tenant context bound for this
 * cross-org job, RLS silently matched zero rows on every refresh, for every
 * org, always. REFRESH itself reported success throughout; nothing failed
 * loudly, which is exactly why this needs a real assertion on row content,
 * not just "the call didn't throw".
 */
class StatsRefreshJobIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StatsRefreshJob statsRefreshJob;
    @Autowired
    private PlotSaleService plotSaleService;
    @Autowired
    private PlotRepository plotRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void refreshNowActuallyPopulatesMvMonthlySalesAcrossOrgsNotJustTheOwningRole() {
        UUID plotId = seedOrgProjectAndPlot();
        LocalDate today = IndianTime.today();
        plotSaleService.create(plotId, new SaleCreateRequest(null, "Stats Refresh Buyer", "9876543217",
                null, null, null, null, today, BigDecimal.valueOf(4_200_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(4_200_000), today)), null));
        TestTenantContext.clear();

        statsRefreshJob.refreshNow();

        // Materialized views have no RLS at all (Postgres doesn't support
        // it on them) -- reading via the plain shardeya_app JdbcTemplate
        // with NO tenant context bound is the correct, real-world shape
        // for this assertion, matching how the fast-path stats queries
        // themselves read these views (their own isolation is the
        // explicit WHERE org_id=... in StatsService, not RLS).
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mv_monthly_sales", Long.class);
        assertThat(count).isGreaterThan(0);

        Long plotsSold = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(plots_sold), 0) FROM mv_monthly_sales WHERE month = date_trunc('month', CAST(? AS date))",
                Long.class, today);
        assertThat(plotsSold).isGreaterThanOrEqualTo(1);
    }

    private UUID seedOrgProjectAndPlot() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Stats Refresh Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        // plot_sale.handled_by is a real FK to app_user -- a random userId
        // with no backing row fails that constraint the moment a sale is
        // created, same as FinancialServiceIntegrationTest's identical setup.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Stats Refresh Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", "9" + String.valueOf(System.nanoTime()).substring(0, 9))
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Stats Refresh Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", BigDecimal.valueOf(1200), "SQ_FT",
                BigDecimal.valueOf(1200), BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);

        return plotId;
    }
}
