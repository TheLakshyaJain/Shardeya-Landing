package com.shardeya.builder.plot;

import com.shardeya.builder.plot.dto.PlotCreateRequest;
import com.shardeya.builder.plot.dto.PlotPositionRequest;
import com.shardeya.builder.plot.dto.PlotResponse;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression coverage for the two DB-backed uniqueness rules on {@code plot}
 * (V2_005: {@code ux_plot_number} on (project_id, plot_number_norm) and
 * {@code ux_plot_cell} on (project_id, grid_row, grid_col)), exercised through
 * the real {@link PlotService} (each of its methods is independently
 * {@code @Transactional}) rather than raw SQL, so this also proves the
 * service's own pre-flight checks -- which exist so a duplicate fails as a
 * clean 409 {@link ConflictException} instead of a raw DB constraint
 * violation -- stay in sync with the DB's normalisation formula.
 *
 * <p>Tenant context is bound once in plain Java before any of these calls,
 * per the binding-before-transaction-opens rule documented on
 * {@code EntitlementServiceIntegrationTest} and CLAUDE.md's M1 notes --
 * {@code TenantAwareDataSource} fixes {@code app.current_org} at connection
 * checkout time, so as long as bind() happens before the FIRST transaction
 * opens, every subsequent independently-transactional service call (each
 * checking out its own connection) still sees the right tenant.
 */
class PlotUniquenessIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlotService plotService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private ProjectRepository projectRepository;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void duplicatePlotNumberIsRejectedEvenWithDifferentPunctuationAndCase() {
        UUID projectId = seedOrgAndProject();

        create(projectId, "P-001", null, null);

        // plot_number_norm strips non-alphanumerics and upper-cases, so
        // "p 001" and "P-001" collide even though the raw strings differ --
        // this is the exact case the pre-flight check exists to catch before
        // the DB's generated-column unique index would.
        assertThatThrownBy(() -> create(projectId, "p 001", null, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void duplicateGridCellIsRejected() {
        UUID projectId = seedOrgAndProject();

        create(projectId, "P-100", 2, 3);

        assertThatThrownBy(() -> create(projectId, "P-101", 2, 3))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void movingAPlotIntoAnOccupiedCellIsRejectedButVacatingAndReoccupyingIsAllowed() {
        UUID projectId = seedOrgAndProject();

        UUID firstPlotId = create(projectId, "P-200", 0, 0).id();
        UUID secondPlotId = create(projectId, "P-201", 0, 1).id();

        assertThatThrownBy(() -> plotService.updatePosition(secondPlotId, new PlotPositionRequest(0, 0)))
                .isInstanceOf(ConflictException.class);

        // Same cell is free again once the original occupant moves elsewhere.
        plotService.updatePosition(firstPlotId, new PlotPositionRequest(5, 5));
        plotService.updatePosition(secondPlotId, new PlotPositionRequest(0, 0));
    }

    private PlotResponse create(UUID projectId, String plotNumber, Integer gridRow, Integer gridCol) {
        return plotService.create(projectId, new PlotCreateRequest(plotNumber, null, null, null,
                BigDecimal.valueOf(1200), "SQ_FT", null, BigDecimal.valueOf(4_200_000), false, false, false,
                null, gridRow, gridCol));
    }

    private UUID seedOrgAndProject() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        TestTenantContext.bind(orgId, UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Uniqueness Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        Project project = new Project(projectId, orgId, "Uniqueness Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        return projectId;
    }
}
