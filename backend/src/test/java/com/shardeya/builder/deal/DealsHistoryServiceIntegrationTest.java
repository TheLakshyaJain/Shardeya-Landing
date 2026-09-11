package com.shardeya.builder.deal;

import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleService;
import com.shardeya.builder.sale.dto.CancelSaleRequest;
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
 * Regression coverage for a real bug found on a live account:
 * {@link DealsHistoryService#list}'s status filter compared a bound
 * PreparedStatement parameter directly against {@code plot_sale.status}
 * (a Postgres enum, {@code plot_sale_status}), which Postgres rejects with
 * "operator does not exist: plot_sale_status = character varying" -- the
 * same class of bug found the same day in
 * {@code FinancialService.payments()}'s mode filter (see that test's own
 * comment). Both were only reachable once a caller actually applied the
 * filter in question.
 */
class DealsHistoryServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DealsHistoryService dealsHistoryService;
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
    void listFilteredByStatusDoesNotThrow() {
        UUID plotId = seedOrgProjectAndPlot();
        LocalDate today = IndianTime.today();
        SaleResponse sale = plotSaleService.create(plotId, new SaleCreateRequest(null, "Status Filter Buyer", "9876543215",
                null, null, null, null, today, BigDecimal.valueOf(3_750_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(3_750_000), today)), null));
        plotSaleService.cancel(sale.id(), new CancelSaleRequest("Buyer backed out", null));

        var cancelledOnly = dealsHistoryService.list("CANCELLED", null, null, null, null, null, null, 10);
        assertThat(cancelledOnly.items()).hasSize(1);
        assertThat(cancelledOnly.items().get(0).saleId()).isEqualTo(sale.id());

        var completedOnly = dealsHistoryService.list("COMPLETED", null, null, null, null, null, null, 10);
        assertThat(completedOnly.items()).isEmpty();
    }

    private UUID seedOrgProjectAndPlot() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of("DATA_VIEW_ALL"));

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Deals History Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Deals Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", "9" + String.valueOf(System.nanoTime()).substring(0, 9))
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Deals History Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", BigDecimal.valueOf(1200), "SQ_FT",
                BigDecimal.valueOf(1200), BigDecimal.valueOf(3_750_000));
        plotRepository.saveAndFlush(plot);

        return plotId;
    }
}
