package com.shardeya.builder.plot;

import com.shardeya.builder.plot.dto.QuickCreateCommitResponse;
import com.shardeya.builder.plot.dto.QuickCreatePreviewResponse;
import com.shardeya.builder.plot.dto.QuickCreateRangeRequest;
import com.shardeya.builder.plot.dto.QuickCreateRequest;
import com.shardeya.builder.plot.dto.QuickCreateSharedPropertiesRequest;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.foundation.calculator.AreaConversionService;
import com.shardeya.foundation.calculator.MeasurementUnitRepository;
import com.shardeya.foundation.subscription.EntitlementService;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.QuotaExceededException;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.shared.AreaMeasure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * B-06 Path A: Quick Range Create. Unlike Path B (M-07's generic import
 * engine, driven by a parsed file), this needs no new tables at all -- it is
 * a service-layer loop that generates plot numbers from one or more numeric
 * ranges and commits them the same way manual plot creation does (unique
 * number check, quota, grid placement), just batched. No entitlement/plan
 * gate applies here (03-BUILDER-MODULES.md B-06 §7: "available on every plan
 * including Free, subject only to the plot-count quota itself") -- contrast
 * with Path B, which is gated by BULK_UPLOAD_ENABLED.
 */
@Service
public class PlotQuickCreateService {

    // Mirrors Path B's own MAX_ROWS cap (ImportService) -- same "split it up"
    // guidance for an implausibly large request, checked on the ARITHMETIC
    // count before ever generating a single string, so a typo like
    // end=2000000000 can't attempt to allocate billions of strings first.
    private static final int ABSOLUTE_CAP = 5_000;
    private static final int COMMIT_CHUNK_SIZE = 200;
    // Same normalisation the DB's generated plot_number_norm column applies
    // (V2_005) -- see PlotService's own identical constant/comment.
    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]");

    private final PlotRepository plotRepository;
    private final ProjectRepository projectRepository;
    private final AreaConversionService areaConversionService;
    private final MeasurementUnitRepository measurementUnitRepository;
    private final EntitlementService entitlementService;
    private final ProjectAccessGuard accessGuard;
    private final TransactionTemplate requiresNewTransaction;

    public PlotQuickCreateService(PlotRepository plotRepository, ProjectRepository projectRepository,
                                   AreaConversionService areaConversionService, MeasurementUnitRepository measurementUnitRepository,
                                   EntitlementService entitlementService, ProjectAccessGuard accessGuard,
                                   PlatformTransactionManager transactionManager) {
        this.plotRepository = plotRepository;
        this.projectRepository = projectRepository;
        this.areaConversionService = areaConversionService;
        this.measurementUnitRepository = measurementUnitRepository;
        this.entitlementService = entitlementService;
        this.accessGuard = accessGuard;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    // Same root cause class M3 already hit with PaymentService.summary():
    // EntitlementService.usage() -> currentUsage() calls
    // entityManager.refresh(u) on the injected persistence context, which
    // requires an ACTIVE transaction even for a read-only call. Without this
    // annotation, Spring Data's own per-repository-method transactions (each
    // opening/closing around a single call) leave nothing open by the time
    // usage() runs. This ONLY surfaces once an org_usage row for
    // (org, BUILDER_PLOTS_PER_PROJECT, project) actually exists -- when it
    // doesn't yet, currentUsage()'s `.orElse(0)` branch never reaches the
    // refresh() call at all. That's exactly why the very first preview test
    // (fresh project, zero plots ever created) passed silently while the
    // collision test (which pre-creates one real plot, firing the
    // org_usage-maintaining trigger) failed with "No EntityManager with
    // actual transaction available for current thread."
    @Transactional(readOnly = true)
    public QuickCreatePreviewResponse preview(UUID projectId, QuickCreateRequest req) {
        Project project = loadProject(projectId);
        List<String> generated = generateNumbers(req.ranges());
        validateSharedProperties(project, req.sharedProperties());

        Set<String> existingNorms = new HashSet<>(plotRepository.findAllNormalisedNumbers(project.getId()));
        Map<String, Integer> occurrences = new HashMap<>();
        for (String number : generated) {
            occurrences.merge(normalize(number), 1, Integer::sum);
        }

        List<QuickCreatePreviewResponse.PlotNumberPreview> previews = new ArrayList<>(generated.size());
        for (String number : generated) {
            String norm = normalize(number);
            previews.add(new QuickCreatePreviewResponse.PlotNumberPreview(
                    number, existingNorms.contains(norm), occurrences.get(norm) > 1));
        }

        EntitlementService.UsageSnapshot usage = entitlementService.usage(project.getOrgId(), "BUILDER_PLOTS_PER_PROJECT", project.getId());
        boolean withinQuota = usage.unlimited() || (usage.used() + generated.size()) <= usage.limit();

        long currentPlotCount = plotRepository.countByProjectIdAndDeletedAtIsNull(project.getId());
        boolean withinDeclaredCount = currentPlotCount + generated.size() <= project.getDeclaredPlotCount();

        return new QuickCreatePreviewResponse(previews, generated.size(), usage.used(), usage.limit(), withinQuota,
                (int) currentPlotCount, project.getDeclaredPlotCount(), withinDeclaredCount);
    }

    // Chunked commit (same 200/transaction pattern as ImportService.commit),
    // so a mid-batch quota breach doesn't roll back plots already created
    // earlier in this same request -- see that class's own comment for the
    // empirically-confirmed reason this matters.
    public QuickCreateCommitResponse commit(UUID projectId, QuickCreateRequest req) {
        Project project = loadProject(projectId);
        List<String> generated = generateNumbers(req.ranges());
        validateSharedProperties(project, req.sharedProperties());
        AreaMeasure area = areaConversionService.toSqft(req.sharedProperties().sizeValue(),
                req.sharedProperties().sizeUnit(), project.getStateCode());

        // Collisions (against existing plots, or a number repeated across the
        // request's own ranges) are skipped rather than failing the whole
        // batch -- the preview already surfaced them for the user to resolve
        // (adjust the range) before ever clicking Create; treating a stale
        // one as a skip rather than a hard error matches Path B's default
        // SKIP duplicate mode and tolerates project state changing between
        // preview and commit.
        Set<String> existingNorms = new HashSet<>(plotRepository.findAllNormalisedNumbers(project.getId()));
        Set<String> seen = new HashSet<>();
        List<String> toCreate = new ArrayList<>();
        for (String number : generated) {
            String norm = normalize(number);
            if (existingNorms.contains(norm) || !seen.add(norm)) {
                continue;
            }
            toCreate.add(number);
        }

        // Unlike the subscription quota (checked per-row inside commitChunk,
        // allowing a partial commit up to the limit), the declared plot count
        // is an all-or-nothing gate: reject the whole batch before writing
        // anything if it would exceed the project's own declared target.
        // Raising the ceiling is only possible by editing the project.
        long currentPlotCount = plotRepository.countByProjectIdAndDeletedAtIsNull(project.getId());
        if (currentPlotCount + toCreate.size() > project.getDeclaredPlotCount()) {
            throw new ConflictException("error.plot.declaredCountExceeded", Map.of(
                    "declared", project.getDeclaredPlotCount(), "current", currentPlotCount, "requested", toCreate.size()));
        }

        // Manually rearranging dozens/hundreds of plots one at a time is a
        // bad default experience -- autoPlace now guarantees a position for
        // every plot whenever the grid has room, not just the ones whose
        // number happens to match the block/number pattern exactly:
        //   1. Block-pattern match first (e.g. "A-5" -> block A, position 5)
        //      when that cell is free -- this is almost always what the
        //      builder actually means by their own numbering.
        //   2. Otherwise, the next open cell in row-major order, so nothing
        //      falls back to the unplaced tray just because its number
        //      doesn't fit the pattern or that one cell is already taken.
        // Manual rearranging via the existing GridLayoutEditor (drag/click)
        // remains fully available afterward for anyone who wants to move
        // things -- this only changes what a good *default* placement is.
        Set<GridPlacementResolver.Position> occupied = new HashSet<>();
        for (PlotRepository.CellPosition cell : plotRepository.findAllOccupiedCells(project.getId())) {
            occupied.add(new GridPlacementResolver.Position(cell.getGridRow(), cell.getGridCol()));
        }
        if (req.autoPlace() && project.getGridRows() == null && project.getGridCols() == null) {
            autoSizeGrid(project, toCreate, occupied.size());
        }

        int created = 0;
        int unplaced = 0;
        for (int i = 0; i < toCreate.size(); i += COMMIT_CHUNK_SIZE) {
            List<String> chunk = toCreate.subList(i, Math.min(i + COMMIT_CHUNK_SIZE, toCreate.size()));
            ChunkResult result = commitChunk(project, chunk, area, req.sharedProperties(), req.autoPlace(), occupied);
            created += result.created;
            unplaced += result.unplaced;
            if (result.quotaHit) {
                break;
            }
        }
        return new QuickCreateCommitResponse(created, unplaced);
    }

    // No grid configured at all yet (a fresh project) -- size one that fits
    // everything about to be created, rather than leaving every plot
    // unplaced for lack of anywhere to put them. Roughly square by default;
    // grown to also cover the largest row/col any block-pattern-matching
    // number in this batch implies (e.g. a "C-30" range needs at least 3
    // rows), so plots whose numbering already encodes a block layout still
    // land exactly where that numbering means, not just wherever fits.
    private void autoSizeGrid(Project project, List<String> toCreate, int alreadyOccupied) {
        int n = toCreate.size();
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (int) Math.ceil((double) n / Math.max(1, cols));

        int maxBlockRow = -1;
        int maxBlockCol = -1;
        for (String number : toCreate) {
            GridPlacementResolver.Position pos = GridPlacementResolver.parseBlockPattern(number);
            if (pos != null) {
                maxBlockRow = Math.max(maxBlockRow, pos.row());
                maxBlockCol = Math.max(maxBlockCol, pos.col());
            }
        }
        rows = Math.max(rows, maxBlockRow + 1);
        cols = Math.max(cols, maxBlockCol + 1);
        rows = Math.max(rows, 1);
        cols = Math.max(cols, 1);

        while ((long) rows * cols < alreadyOccupied + n) {
            cols++;
        }

        project.setGridRows(rows);
        project.setGridCols(cols);
        projectRepository.save(project);
    }

    // Row-major scan for the next cell not already in `occupied`. Null if
    // the grid genuinely has no room left -- the plot then falls back to
    // the unplaced tray exactly as before, the only remaining case where
    // that happens.
    private GridPlacementResolver.Position findNextOpenCell(Project project, Set<GridPlacementResolver.Position> occupied) {
        if (project.getGridRows() == null || project.getGridCols() == null) {
            return null;
        }
        for (int row = 0; row < project.getGridRows(); row++) {
            for (int col = 0; col < project.getGridCols(); col++) {
                GridPlacementResolver.Position candidate = new GridPlacementResolver.Position(row, col);
                if (!occupied.contains(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private record ChunkResult(int created, int unplaced, boolean quotaHit) {
    }

    private ChunkResult commitChunk(Project project, List<String> plotNumbers, AreaMeasure area,
                                     QuickCreateSharedPropertiesRequest shared, boolean autoPlace,
                                     Set<GridPlacementResolver.Position> occupied) {
        return requiresNewTransaction.execute(status -> {
            int created = 0;
            int unplaced = 0;
            for (String plotNumber : plotNumbers) {
                try {
                    entitlementService.assertWithinQuota(project.getOrgId(), "BUILDER_PLOTS_PER_PROJECT", project.getId(),
                            "error.plot.quotaExceeded");
                } catch (QuotaExceededException e) {
                    // Same reasoning as ImportService.commitChunk: breaking
                    // here (instead of letting this propagate out of the
                    // TransactionTemplate callback) keeps every plot created
                    // so far in THIS chunk committed, rather than rolling
                    // back the whole chunk's transaction.
                    return new ChunkResult(created, unplaced, true);
                }

                Integer row = null;
                Integer col = null;
                if (autoPlace) {
                    GridPlacementResolver.Position pos = GridPlacementResolver.parseBlockPattern(plotNumber);
                    if (pos == null || GridPlacementResolver.isOutOfBounds(project, pos.row(), pos.col())
                            || occupied.contains(pos)) {
                        pos = findNextOpenCell(project, occupied);
                    }
                    if (pos != null) {
                        row = pos.row();
                        col = pos.col();
                        occupied.add(pos);
                    }
                }
                if (row == null) unplaced++;

                Plot plot = new Plot(UUID.randomUUID(), project.getOrgId(), project.getId(), plotNumber,
                        area.value(), area.unit(), area.sqft(), shared.price());
                plot.setStatus(Plot.Status.AVAILABLE);
                plot.setFacing(shared.facing());
                plot.setGarden(shared.isGarden());
                plot.setCorner(shared.isCorner());
                plot.setHot(shared.isHot());
                plot.setRemarks(shared.remarks());
                plot.setGridRow(row);
                plot.setGridCol(col);
                plotRepository.save(plot);
                created++;
            }
            return new ChunkResult(created, unplaced, false);
        });
    }

    // Deliberately checks the ARITHMETIC count of every range before
    // generating a single string (B-06 §10: "range spanning an implausibly
    // large count... same cap and 'split it up' guidance") -- an
    // end=2_000_000_000 typo must fail immediately, not after however long
    // it takes to build that many strings.
    private List<String> generateNumbers(List<QuickCreateRangeRequest> ranges) {
        long totalCount = 0;
        for (QuickCreateRangeRequest range : ranges) {
            if (range.end() < range.start()) {
                throw new BadRequestException("ranges", "END_BEFORE_START", "error.plot.quickCreate.endBeforeStart");
            }
            totalCount += (long) range.end() - range.start() + 1;
            if (totalCount > ABSOLUTE_CAP) {
                throw new BadRequestException("ranges", "QUICK_CREATE_TOO_MANY", "error.plot.quickCreate.tooMany");
            }
        }

        List<String> result = new ArrayList<>((int) totalCount);
        for (QuickCreateRangeRequest range : ranges) {
            String prefix = range.prefix() == null ? "" : range.prefix().trim();
            String separator = prefix.isEmpty() ? "" : (range.separator() == null ? "" : range.separator());
            int padWidth = range.padWidth() == null ? 0 : range.padWidth();
            for (int n = range.start(); n <= range.end(); n++) {
                String numStr = padWidth > 0 ? String.format("%0" + padWidth + "d", n) : String.valueOf(n);
                result.add(prefix + separator + numStr);
            }
        }
        return result;
    }

    private void validateSharedProperties(Project project, QuickCreateSharedPropertiesRequest shared) {
        if (measurementUnitRepository.findBestMatch(shared.sizeUnit(), project.getStateCode()).isEmpty()) {
            throw new BadRequestException("sharedProperties.sizeUnit", "UNKNOWN_UNIT", "error.area.unitNotFound");
        }
    }

    private String normalize(String plotNumber) {
        return NON_ALNUM.matcher(plotNumber).replaceAll("").toUpperCase();
    }

    private Project loadProject(UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(project.getId());
        return project;
    }
}
