package com.shardeya.builder.stats;

import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleService;
import com.shardeya.builder.sale.dto.SaleCreateRequest;
import com.shardeya.builder.sale.dto.ScheduleRowRequest;
import com.shardeya.builder.stats.dto.BreakdownSlice;
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

class StatsServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StatsService statsService;
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
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void revenueByProjectIncludesAMonthEvenWhenTheDateRangePicksAPartialMonth() {
        UUID orgId = seedOrgWithAdvancedAnalytics();
        UUID plotId = seedProjectAndPlot(orgId, "Revenue By Project Test");
        LocalDate today = IndianTime.today();
        plotSaleService.create(plotId, new SaleCreateRequest(null, "Revenue Filter Buyer", "9876543218",
                null, null, null, null, today, BigDecimal.valueOf(4_200_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(4_200_000), today)), null));
        statsRefreshJob.refreshNow();

        // Real bug: m.month always stores the first of a month, so a
        // BETWEEN comparison against raw picked dates that don't start on
        // the 1st silently dropped the whole month. Here the range starts
        // and ends mid-month around `today`, deliberately never landing on
        // a month-start, to reproduce exactly that shape.
        List<BreakdownSlice> result = statsService.revenueByProject(today.withDayOfMonth(Math.max(1, today.getDayOfMonth() - 1)), today);

        BreakdownSlice row = result.stream().filter(r -> r.label().equals("Revenue By Project Test")).findFirst().orElseThrow();
        assertThat(row.count()).isEqualTo(1L);
        assertThat(row.value()).isEqualByComparingTo(BigDecimal.valueOf(4_200_000));
    }

    private UUID seedOrgWithAdvancedAnalytics() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of("REPORT_VIEW_ALL", "FINANCIAL_VIEW"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Organization org = new Organization(orgId, Organization.Type.BUILDER, "Stats Service Test Org", "Jaipur");
            organizationRepository.saveAndFlush(org);

            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Stats Service Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId).setParameter("orgId", orgId)
                    .setParameter("mobile", "9" + String.valueOf(System.nanoTime()).substring(0, 9))
                    .setParameter("roleId", roleId).executeUpdate();

            // revenue-by-project requires ANALYTICS >= ADVANCED
            // (AnalyticsTier); FREE (the default with no subscription row)
            // only grants BASIC. Same throwaway-plan-via-direct-SQL
            // precedent every milestone since M4 has used.
            entityManager.createNativeQuery(
                            "INSERT INTO plan (code, name_en, name_hi, price_monthly, price_yearly, sort_order) "
                                    + "VALUES ('STATSTEST', 'Stats Test Plan', 'Stats Test Plan', 0, 0, 98) "
                                    + "ON CONFLICT (code) DO NOTHING")
                    .executeUpdate();
            entityManager.createNativeQuery(
                            "INSERT INTO plan_limit (plan_code, limit_key, limit_value) VALUES ('STATSTEST', 'ANALYTICS', 'ADVANCED') "
                                    + "ON CONFLICT (plan_code, limit_key) DO NOTHING")
                    .executeUpdate();
            entityManager.createNativeQuery(
                            "INSERT INTO subscription (id, org_id, plan_code, status, started_at, current_period_start, current_period_end) "
                                    + "VALUES (:id, :orgId, 'STATSTEST', 'ACTIVE', now(), now(), now() + interval '100 years')")
                    .setParameter("id", UUID.randomUUID()).setParameter("orgId", orgId).executeUpdate();
        });
        return orgId;
    }

    private UUID seedProjectAndPlot(UUID orgId, String projectName) {
        UUID projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        Project project = new Project(projectId, orgId, projectName, Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);
        Plot plot = new Plot(plotId, orgId, projectId, "A-1", BigDecimal.valueOf(1200), "SQ_FT",
                BigDecimal.valueOf(1200), BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);
        return plotId;
    }
}
