package com.shardeya.builder.plot;

import com.shardeya.builder.plot.dto.QuickCreateCommitResponse;
import com.shardeya.builder.plot.dto.QuickCreatePreviewResponse;
import com.shardeya.builder.plot.dto.QuickCreateRangeRequest;
import com.shardeya.builder.plot.dto.QuickCreateRequest;
import com.shardeya.builder.plot.dto.QuickCreateSharedPropertiesRequest;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B-06 Path A (Quick Range Create): range parsing, collision detection
 * (against existing plots and within the request's own overlapping ranges),
 * the absolute per-submission cap, quota enforcement during commit, and
 * auto-placement reusing the exact same block-pattern logic as Path B
 * (Excel import) via {@link GridPlacementResolver}.
 */
class PlotQuickCreateServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlotQuickCreateService quickCreateService;
    @Autowired
    private PlotRepository plotRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private OrganizationRepository organizationRepository;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    private UUID currentOrgId;

    private UUID seedOrgAndProject() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        TestTenantContext.bind(orgId, UUID.randomUUID(), "BUILDER", "BUILDER_ADMIN", Set.of());
        currentOrgId = orgId;

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Quick Create Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);
        Project project = new Project(projectId, orgId, "Quick Create Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);
        return projectId;
    }

    private QuickCreateSharedPropertiesRequest sharedProps() {
        return new QuickCreateSharedPropertiesRequest(BigDecimal.valueOf(1200), "SQ_FT", Plot.Facing.E,
                BigDecimal.valueOf(4_200_000), false, false, false, null);
    }

    @Test
    void previewGeneratesPrefixSeparatorAndZeroPaddedNumbers() {
        UUID projectId = seedOrgAndProject();
        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest("A", "-", 1, 3, 3)),
                sharedProps(), true);

        QuickCreatePreviewResponse preview = quickCreateService.preview(projectId, req);

        assertThat(preview.totalCount()).isEqualTo(3);
        assertThat(preview.plotNumbers().stream().map(QuickCreatePreviewResponse.PlotNumberPreview::plotNumber))
                .containsExactly("A-001", "A-002", "A-003");
        assertThat(preview.plotNumbers()).allMatch(p -> !p.collidesWithExisting() && !p.collidesWithinRequest());
    }

    @Test
    void previewWithNoPrefixGeneratesBareNumbers() {
        UUID projectId = seedOrgAndProject();
        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest(null, null, 1, 5, 0)),
                sharedProps(), true);

        QuickCreatePreviewResponse preview = quickCreateService.preview(projectId, req);

        assertThat(preview.plotNumbers().stream().map(QuickCreatePreviewResponse.PlotNumberPreview::plotNumber))
                .containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    void previewFlagsCollisionWithAnExistingPlot() {
        UUID projectId = seedOrgAndProject();
        // Pre-existing plot that WILL collide with the generated range.
        Plot existing = new Plot(UUID.randomUUID(), currentOrgId, projectId, "A-2",
                BigDecimal.valueOf(1200), "SQ_FT", BigDecimal.valueOf(1200), BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(existing);

        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest("A", "-", 1, 3, 0)),
                sharedProps(), true);
        QuickCreatePreviewResponse preview = quickCreateService.preview(projectId, req);

        assertThat(preview.plotNumbers()).anySatisfy(p -> {
            assertThat(p.plotNumber()).isEqualTo("A-2");
            assertThat(p.collidesWithExisting()).isTrue();
        });
        assertThat(preview.plotNumbers().stream().filter(p -> !p.plotNumber().equals("A-2")))
                .allMatch(p -> !p.collidesWithExisting());
    }

    @Test
    void previewFlagsInternalCollisionsAcrossOverlappingRanges() {
        UUID projectId = seedOrgAndProject();
        // A1-A50 and A25-A75 overlap on A-25..A-50.
        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest("A", "-", 1, 50, 0), new QuickCreateRangeRequest("A", "-", 25, 75, 0)),
                sharedProps(), true);

        QuickCreatePreviewResponse preview = quickCreateService.preview(projectId, req);

        assertThat(preview.totalCount()).isEqualTo(50 + 51); // both ranges' raw counts, overlap included
        long flaggedAsInternalCollision = preview.plotNumbers().stream().filter(QuickCreatePreviewResponse.PlotNumberPreview::collidesWithinRequest).count();
        assertThat(flaggedAsInternalCollision).isEqualTo(52); // A-25..A-50 (26 numbers) appear twice each
    }

    @Test
    void endBeforeStartIsRejected() {
        UUID projectId = seedOrgAndProject();
        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest("A", "-", 10, 5, 0)),
                sharedProps(), true);

        assertThatThrownBy(() -> quickCreateService.preview(projectId, req)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rangeExceedingTheAbsoluteCapIsRejectedBeforeGeneratingAnything() {
        UUID projectId = seedOrgAndProject();
        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest("A", "-", 1, 6000, 0)),
                sharedProps(), true);

        assertThatThrownBy(() -> quickCreateService.preview(projectId, req)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void commitCreatesAvailablePlotsWithSharedPropertiesAndAutoPlacesThem() {
        UUID projectId = seedOrgAndProject();
        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest("A", "-", 1, 5, 0)),
                sharedProps(), true);

        QuickCreateCommitResponse result = quickCreateService.commit(projectId, req);

        assertThat(result.created()).isEqualTo(5);
        assertThat(result.unplaced()).isEqualTo(0); // "A-N" always matches the block pattern

        List<Plot> plots = plotRepository.findByProjectIdAndDeletedAtIsNull(projectId);
        assertThat(plots).hasSize(5);
        assertThat(plots).allMatch(p -> p.getStatus() == Plot.Status.AVAILABLE);
        assertThat(plots).allMatch(p -> p.getPrice().compareTo(BigDecimal.valueOf(4_200_000)) == 0);
        assertThat(plots).allMatch(p -> p.getFacing() == Plot.Facing.E);
        // A-1 -> block A (index 1) -> row 0, number 1 -> col 0.
        Plot a1 = plots.stream().filter(p -> p.getPlotNumber().equals("A-1")).findFirst().orElseThrow();
        assertThat(a1.getGridRow()).isEqualTo(0);
        assertThat(a1.getGridCol()).isEqualTo(0);
    }

    @Test
    void commitSkipsCollisionsInsteadOfFailingTheWholeBatch() {
        UUID projectId = seedOrgAndProject();
        Plot existing = new Plot(UUID.randomUUID(), currentOrgId, projectId, "A-2",
                BigDecimal.valueOf(1200), "SQ_FT", BigDecimal.valueOf(1200), BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(existing);

        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest("A", "-", 1, 3, 0)),
                sharedProps(), true);
        QuickCreateCommitResponse result = quickCreateService.commit(projectId, req);

        // Only A-1 and A-3 are genuinely new; A-2 already existed and must be skipped, not duplicated or errored.
        assertThat(result.created()).isEqualTo(2);
        assertThat(plotRepository.findByProjectIdAndDeletedAtIsNull(projectId)).hasSize(3); // pre-existing A-2 + 2 new
    }

    @Test
    void commitStopsAtTheFreePlanQuotaAndKeepsPlotsAlreadyCreated() {
        UUID projectId = seedOrgAndProject();
        // FREE plan's BUILDER_PLOTS_PER_PROJECT is seeded at 50 (V2_009).
        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest("A", "-", 1, 60, 0)),
                sharedProps(), true);

        QuickCreateCommitResponse result = quickCreateService.commit(projectId, req);

        assertThat(result.created()).isEqualTo(50);
        assertThat(plotRepository.findByProjectIdAndDeletedAtIsNull(projectId)).hasSize(50);
    }

    @Test
    void commitAutoSizesTheGridWhenNoneIsConfiguredYet() {
        UUID projectId = seedOrgAndProject();
        assertThat(projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow().getGridRows()).isNull();

        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest("A", "-", 1, 5, 0)),
                sharedProps(), true);
        quickCreateService.commit(projectId, req);

        Project reloaded = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        assertThat(reloaded.getGridRows()).isNotNull();
        assertThat(reloaded.getGridCols()).isNotNull();
        assertThat((long) reloaded.getGridRows() * reloaded.getGridCols()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void commitAutoSizesTheGridWideEnoughForTheHighestBlockColumnImplied() {
        UUID projectId = seedOrgAndProject();
        // "C-30" implies block C (row 2) and column index 29 -- the grid
        // must be sized to actually fit that, not just a generic sqrt(30)
        // square that would leave it out of bounds.
        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest("C", "-", 1, 30, 0)),
                sharedProps(), true);
        quickCreateService.commit(projectId, req);

        Project reloaded = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        assertThat(reloaded.getGridRows()).isGreaterThanOrEqualTo(3);
        assertThat(reloaded.getGridCols()).isGreaterThanOrEqualTo(30);

        Plot c30 = plotRepository.findByProjectIdAndDeletedAtIsNull(projectId).stream()
                .filter(p -> p.getPlotNumber().equals("C-30")).findFirst().orElseThrow();
        assertThat(c30.getGridRow()).isEqualTo(2);
        assertThat(c30.getGridCol()).isEqualTo(29);
    }

    @Test
    void commitFallsBackToTheNextOpenCellWhenTheBlockPatternDoesNotMatch() {
        UUID projectId = seedOrgAndProject();
        // Bare numbers with no letter prefix at all don't match the
        // Block-Number pattern (it requires at least one leading letter),
        // so every single one used to land in the unplaced tray. They must
        // now all still get a real position.
        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest(null, null, 1, 4, 0)),
                sharedProps(), true);

        QuickCreateCommitResponse result = quickCreateService.commit(projectId, req);

        assertThat(result.created()).isEqualTo(4);
        assertThat(result.unplaced()).isEqualTo(0);
        List<Plot> plots = plotRepository.findByProjectIdAndDeletedAtIsNull(projectId);
        assertThat(plots).allMatch(p -> p.getGridRow() != null && p.getGridCol() != null);
        // No two plots share the same cell.
        assertThat(plots.stream().map(p -> p.getGridRow() + "," + p.getGridCol()).distinct().count()).isEqualTo(4);
    }

    @Test
    void commitFallsBackToTheNextOpenCellWhenTheBlockPatternCellIsAlreadyTaken() {
        UUID projectId = seedOrgAndProject();
        // Pre-existing plot occupying exactly where "A-1" would otherwise
        // land (row 0, col 0), on a pre-configured, generously-sized grid.
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        project.setGridRows(5);
        project.setGridCols(5);
        projectRepository.saveAndFlush(project);
        Plot occupant = new Plot(UUID.randomUUID(), currentOrgId, projectId, "Existing-1",
                BigDecimal.valueOf(1200), "SQ_FT", BigDecimal.valueOf(1200), BigDecimal.valueOf(4_200_000));
        occupant.setGridRow(0);
        occupant.setGridCol(0);
        plotRepository.saveAndFlush(occupant);

        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest("A", "-", 1, 1, 0)),
                sharedProps(), true);
        QuickCreateCommitResponse result = quickCreateService.commit(projectId, req);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.unplaced()).isEqualTo(0);
        Plot a1 = plotRepository.findByProjectIdAndDeletedAtIsNull(projectId).stream()
                .filter(p -> p.getPlotNumber().equals("A-1")).findFirst().orElseThrow();
        // Its natural cell (0,0) was taken, so it landed somewhere else instead of the unplaced tray.
        assertThat(a1.getGridRow() != 0 || a1.getGridCol() != 0).isTrue();
    }

    @Test
    void commitLeavesPlotsUnplacedOnlyWhenAPreConfiguredGridIsGenuinelyFull() {
        UUID projectId = seedOrgAndProject();
        // A pre-existing, deliberately tiny 1x1 grid -- already at capacity,
        // and NOT auto-resized since the user explicitly configured it.
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        project.setGridRows(1);
        project.setGridCols(1);
        projectRepository.saveAndFlush(project);

        var req = new QuickCreateRequest(
                List.of(new QuickCreateRangeRequest("A", "-", 1, 3, 0)),
                sharedProps(), true);
        QuickCreateCommitResponse result = quickCreateService.commit(projectId, req);

        assertThat(result.created()).isEqualTo(3);
        assertThat(result.unplaced()).isEqualTo(2); // only one cell exists in the whole grid
    }
}
