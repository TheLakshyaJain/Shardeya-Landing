package com.shardeya.builder.plot;

import com.shardeya.builder.plot.dto.BulkPositionRequest;
import com.shardeya.builder.plot.dto.BulkUpdateRequest;
import com.shardeya.builder.plot.dto.GridResponse;
import com.shardeya.builder.plot.dto.PlotCreateRequest;
import com.shardeya.builder.plot.dto.PlotHotRequest;
import com.shardeya.builder.plot.dto.PlotPositionRequest;
import com.shardeya.builder.plot.dto.PlotResponse;
import com.shardeya.builder.plot.dto.PlotStatsResponse;
import com.shardeya.builder.plot.dto.PlotStatusUpdateRequest;
import com.shardeya.builder.plot.dto.PlotUpdateRequest;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.foundation.calculator.AreaConversionService;
import com.shardeya.foundation.subscription.EntitlementService;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.shared.AreaMeasure;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PlotService {

    // Mirrors the DB's generated column formula EXACTLY (V2_005:
    // upper(regexp_replace(plot_number, '[^A-Za-z0-9]', '', 'g'))) so the
    // pre-flight uniqueness check and the DB's own unique index never
    // disagree. If that migration's formula ever changes, this must change
    // with it — there's no way to derive one from the other automatically.
    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]");

    private final PlotRepository repository;
    private final ProjectRepository projectRepository;
    private final AreaConversionService areaConversionService;
    private final EntitlementService entitlementService;
    private final ProjectAccessGuard accessGuard;
    private final EntityManager entityManager;

    public PlotService(PlotRepository repository, ProjectRepository projectRepository,
                        AreaConversionService areaConversionService, EntitlementService entitlementService,
                        ProjectAccessGuard accessGuard, EntityManager entityManager) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.areaConversionService = areaConversionService;
        this.entitlementService = entitlementService;
        this.accessGuard = accessGuard;
        this.entityManager = entityManager;
    }

    // price_per_unit and plot_number_norm are DB GENERATED ALWAYS ... STORED
    // columns (V2_005) mapped insertable=false/updatable=false — Hibernate
    // never populates them into the in-memory entity on save(), only on a
    // fresh SELECT. Without this, create()/update() would echo back
    // pricePerUnit=null (or stale) instead of the value the DB just computed,
    // violating CLAUDE.md's "mutations return the full updated resource".
    private void refresh(Plot plot) {
        entityManager.flush();
        entityManager.refresh(plot);
    }

    @Transactional
    public PlotResponse create(UUID projectId, PlotCreateRequest req) {
        Project project = loadProject(projectId);

        if (req.status() == Plot.Status.SOLD) {
            // B-03 §7: SOLD is never settable directly — it's a side effect
            // of creating a plot_sale (B-04/M3). Not implemented yet, so this
            // is a hard block rather than a silent downgrade.
            throw new BadRequestException("status", "PLOT_SOLD_NOT_DIRECT", "error.plot.soldNotDirect");
        }
        if (req.status() == Plot.Status.RESERVED && (req.reservedFor() == null || req.reservedFor().isBlank())) {
            throw new BadRequestException("reservedFor", "RESERVED_FOR_REQUIRED", "error.plot.reservedForRequired");
        }

        String norm = normalize(req.plotNumber());
        if (repository.findByNormalisedNumber(project.getId(), norm).isPresent()) {
            throw new ConflictException("error.plot.numberTaken");
        }

        entitlementService.assertWithinQuota(project.getOrgId(), "BUILDER_PLOTS_PER_PROJECT", project.getId(),
                "error.plot.quotaExceeded");
        assertWithinDeclaredCount(project, 1);

        if (req.gridRow() != null && req.gridCol() != null) {
            assertCellFree(project, req.gridRow(), req.gridCol(), null);
        }

        AreaMeasure area = areaConversionService.toSqft(req.sizeValue(), req.sizeUnit(), project.getStateCode());

        Plot plot = new Plot(UUID.randomUUID(), project.getOrgId(), project.getId(), req.plotNumber(),
                area.value(), area.unit(), area.sqft(), req.price());
        if (req.status() != null) plot.setStatus(req.status());
        plot.setReservedFor(req.reservedFor());
        plot.setReservedUntil(req.reservedUntil() != null ? req.reservedUntil()
                : (req.status() == Plot.Status.RESERVED ? LocalDate.now().plusDays(15) : null));
        plot.setFacing(req.facing());
        plot.setGarden(req.isGarden());
        plot.setCorner(req.isCorner());
        plot.setHot(req.isHot());
        plot.setRemarks(req.remarks());
        plot.setGridRow(req.gridRow());
        plot.setGridCol(req.gridCol());

        // plot.id is a manually-assigned UUID, not @GeneratedValue, so Spring
        // Data's isNew() (id != null) always resolves false for this
        // brand-new transient instance -> save() routes through
        // entityManager.merge(), not persist(). merge() returns a NEW managed
        // instance and leaves the argument detached forever, so the return
        // value MUST be captured — refreshing the original `plot` reference
        // throws "Entity not managed".
        plot = repository.save(plot);
        refresh(plot);
        return toResponse(plot);
    }

    public PlotResponse get(UUID plotId) {
        Plot plot = repository.findByIdAndDeletedAtIsNull(plotId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(plot.getProjectId());
        return toResponse(plot);
    }

    public List<PlotResponse> list(UUID projectId, Plot.Status status, Plot.Facing facing,
                                          BigDecimal sizeMin, BigDecimal sizeMax, BigDecimal priceMin, BigDecimal priceMax,
                                          Boolean isHot, Boolean isCorner, Boolean isGarden, String search,
                                          int limit) {
        Project project = loadProject(projectId);
        Specification<Plot> spec = PlotSpecifications.combine(
                PlotSpecifications.forProject(project.getId()), PlotSpecifications.status(status),
                PlotSpecifications.facing(facing), PlotSpecifications.sizeMin(sizeMin), PlotSpecifications.sizeMax(sizeMax),
                PlotSpecifications.priceMin(priceMin), PlotSpecifications.priceMax(priceMax),
                PlotSpecifications.isHot(isHot), PlotSpecifications.isCorner(isCorner), PlotSpecifications.isGarden(isGarden),
                PlotSpecifications.search(search));
        return repository.findAll(spec, PageRequest.of(0, Math.min(Math.max(limit, 1), 500), Sort.by(Sort.Direction.ASC, "plotNumber")))
                .map(this::toResponse).toList();
    }

    @Transactional
    public PlotResponse update(UUID plotId, PlotUpdateRequest req) {
        Plot plot = repository.findByIdAndDeletedAtIsNull(plotId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(plot.getProjectId());
        // A real gap found live: nothing here stopped a plot's own
        // deal-defining attributes (number, size, facing, price) from being
        // edited after it was sold -- including after the sale itself
        // reached COMPLETED via "Mark Complete." The frontend's Edit button
        // is Delete's own sibling in PlotDetailDrawer and already hides
        // whenever plot.status === 'SOLD' (CLAUDE.md rule #5: that hide is
        // cosmetic, this is the real enforcement). Every SOLD plot's own
        // dedicated endpoints (/hot, /position, /status) are untouched --
        // only this general-attribute PATCH is blocked, since updatePlot()
        // is this endpoint's only caller (PlotForm.tsx's edit mode) and no
        // other flow needs to reach it once a plot has a sale attached.
        if (plot.getStatus() == Plot.Status.SOLD) {
            throw new BadRequestException("status", "PLOT_EDIT_BLOCKED_SOLD", "error.plot.editBlockedSold");
        }
        Project project = loadProject(plot.getProjectId());

        if (req.plotNumber() != null && !req.plotNumber().equals(plot.getPlotNumber())) {
            String norm = normalize(req.plotNumber());
            repository.findByNormalisedNumber(project.getId(), norm).ifPresent(existing -> {
                if (!existing.getId().equals(plot.getId())) {
                    throw new ConflictException("error.plot.numberTaken");
                }
            });
            plot.setPlotNumber(req.plotNumber());
        }
        if (req.sizeValue() != null && req.sizeUnit() != null) {
            AreaMeasure area = areaConversionService.toSqft(req.sizeValue(), req.sizeUnit(), project.getStateCode());
            plot.setSizeValue(area.value());
            plot.setSizeUnit(area.unit());
            plot.setSizeSqft(area.sqft());
        }
        if (req.facing() != null) plot.setFacing(req.facing());
        // The previous "can't zero out price on a SOLD plot" check that
        // lived here is now unreachable and was removed -- the guard added
        // above already rejects the whole update() call before this line is
        // ever reached for a sold plot. req.price()'s own @PositiveOrZero
        // validation still allows 0 for a not-yet-sold plot (a "price TBD"
        // placeholder), which was always the intended base case.
        if (req.price() != null) {
            plot.setPrice(req.price());
        }
        if (req.isGarden() != null) plot.setGarden(req.isGarden());
        if (req.isCorner() != null) plot.setCorner(req.isCorner());
        if (req.isHot() != null) plot.setHot(req.isHot());
        if (req.remarks() != null) plot.setRemarks(req.remarks());

        repository.save(plot);
        refresh(plot);
        return toResponse(plot);
    }

    // B-03 §7 status-transition guard: AVAILABLE<->RESERVED only this
    // milestone. SOLD is never reachable here (plot_sale doesn't exist until
    // M3) and RESERVED/AVAILABLE -> SOLD is rejected explicitly rather than
    // silently ignored, so a client bug fails loudly instead of leaving a
    // plot stuck.
    @Transactional
    public PlotResponse updateStatus(UUID plotId, PlotStatusUpdateRequest req) {
        Plot plot = repository.findByIdAndDeletedAtIsNull(plotId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(plot.getProjectId());

        if (req.status() == Plot.Status.SOLD || plot.getStatus() == Plot.Status.SOLD) {
            throw new BadRequestException("status", "PLOT_SOLD_NOT_DIRECT", "error.plot.soldNotDirect");
        }
        if (req.status() == Plot.Status.RESERVED) {
            if (req.reservedFor() == null || req.reservedFor().isBlank()) {
                throw new BadRequestException("reservedFor", "RESERVED_FOR_REQUIRED", "error.plot.reservedForRequired");
            }
            plot.setReservedFor(req.reservedFor());
            plot.setReservedUntil(req.reservedUntil() != null ? req.reservedUntil() : LocalDate.now().plusDays(15));
        } else {
            plot.setReservedFor(null);
            plot.setReservedUntil(null);
        }
        plot.setStatus(req.status());
        repository.save(plot);
        return toResponse(plot);
    }

    @Transactional
    public PlotResponse updateHot(UUID plotId, PlotHotRequest req) {
        Plot plot = repository.findByIdAndDeletedAtIsNull(plotId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(plot.getProjectId());
        plot.setHot(req.isHot());
        repository.save(plot);
        return toResponse(plot);
    }

    @Transactional
    public PlotResponse updatePosition(UUID plotId, PlotPositionRequest req) {
        Plot plot = repository.findByIdAndDeletedAtIsNull(plotId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(plot.getProjectId());
        Project project = loadProject(plot.getProjectId());

        assertCellFree(project, req.gridRow(), req.gridCol(), plot.getId());
        plot.setGridRow(req.gridRow());
        plot.setGridCol(req.gridCol());
        repository.save(plot);
        return toResponse(plot);
    }

    @Transactional
    public void bulkPosition(UUID projectId, BulkPositionRequest req) {
        Project project = loadProject(projectId);
        for (BulkPositionRequest.Placement placement : req.placements()) {
            Plot plot = repository.findByIdAndDeletedAtIsNull(placement.plotId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
            assertCellFree(project, placement.gridRow(), placement.gridCol(), plot.getId());
            plot.setGridRow(placement.gridRow());
            plot.setGridCol(placement.gridCol());
            repository.save(plot);
        }
    }

    @Transactional
    public int bulkUpdate(UUID projectId, BulkUpdateRequest req) {
        Project project = loadProject(projectId);
        if (req.status() == Plot.Status.SOLD) {
            throw new BadRequestException("status", "PLOT_SOLD_NOT_DIRECT", "error.plot.soldNotDirect");
        }
        int updated = 0;
        for (UUID plotId : req.plotIds()) {
            Plot plot = repository.findByIdAndDeletedAtIsNull(plotId).orElse(null);
            if (plot == null || !plot.getProjectId().equals(project.getId())) continue;
            if (req.pricePerSqft() != null) {
                plot.setPrice(req.pricePerSqft().multiply(plot.getSizeSqft()).setScale(2, RoundingMode.HALF_UP));
            }
            if (req.status() != null) {
                plot.setStatus(req.status());
            }
            repository.save(plot);
            updated++;
        }
        return updated;
    }

    @Transactional
    public void delete(UUID plotId) {
        Plot plot = repository.findByIdAndDeletedAtIsNull(plotId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(plot.getProjectId());
        plot.setDeletedAt(Instant.now());
        repository.save(plot);
    }

    public GridResponse grid(UUID projectId) {
        Project project = loadProject(projectId);
        List<Plot> plots = repository.findByProjectIdAndDeletedAtIsNull(project.getId());

        List<Object[]> tuples = new ArrayList<>();
        List<UUID> unplaced = new ArrayList<>();
        for (Plot p : plots) {
            if (p.getGridRow() == null || p.getGridCol() == null) {
                unplaced.add(p.getId());
                continue;
            }
            int statusCode = switch (p.getStatus()) {
                case AVAILABLE -> 1;
                case RESERVED -> 2;
                case SOLD -> 3;
            };
            tuples.add(new Object[] { p.getGridRow(), p.getGridCol(), p.getPlotNumber(), statusCode,
                    p.getSizeSqft(), p.isHot() ? 1 : 0 });
        }

        List<int[]> blocked = readBlockedCells(project.getGridLayout());

        return new GridResponse(
                project.getGridRows() == null ? 0 : project.getGridRows(),
                project.getGridCols() == null ? 0 : project.getGridCols(),
                blocked, tuples,
                new GridResponse.Legend(Map.of("1", "AVAILABLE", "2", "RESERVED", "3", "SOLD")),
                unplaced);
    }

    public PlotStatsResponse stats(UUID projectId) {
        Project project = loadProject(projectId);
        List<Plot> plots = repository.findByProjectIdAndDeletedAtIsNull(project.getId());

        long available = 0, reserved = 0, sold = 0;
        BigDecimal availableValue = BigDecimal.ZERO, reservedValue = BigDecimal.ZERO, soldValue = BigDecimal.ZERO;
        for (Plot p : plots) {
            switch (p.getStatus()) {
                case AVAILABLE -> { available++; availableValue = availableValue.add(p.getPrice()); }
                case RESERVED -> { reserved++; reservedValue = reservedValue.add(p.getPrice()); }
                case SOLD -> { sold++; soldValue = soldValue.add(p.getPrice()); }
            }
        }
        BigDecimal total = availableValue.add(reservedValue).add(soldValue);
        return new PlotStatsResponse(plots.size(), available, reserved, sold, total, availableValue, reservedValue, soldValue);
    }

    private Project loadProject(UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(project.getId());
        return project;
    }

    // declaredPlotCount is a project-level target the builder sets at
    // creation (or via edit) -- distinct from the subscription plan's
    // BUILDER_PLOTS_PER_PROJECT quota, which is checked separately just
    // above. Raising this ceiling is only possible by editing the project's
    // own declared plot count, never by adding plots past it.
    private void assertWithinDeclaredCount(Project project, int additionalCount) {
        long current = repository.countByProjectIdAndDeletedAtIsNull(project.getId());
        if (current + additionalCount > project.getDeclaredPlotCount()) {
            throw new ConflictException("error.plot.declaredCountExceeded", Map.of(
                    "declared", project.getDeclaredPlotCount(), "current", current, "requested", additionalCount));
        }
    }

    private void assertCellFree(Project project, int row, int col, UUID excludingPlotId) {
        if (project.getGridRows() != null && row >= project.getGridRows()) {
            throw new BadRequestException("gridRow", "GRID_ROW_OUT_OF_BOUNDS", "error.plot.positionOutOfBounds");
        }
        if (project.getGridCols() != null && col >= project.getGridCols()) {
            throw new BadRequestException("gridCol", "GRID_COL_OUT_OF_BOUNDS", "error.plot.positionOutOfBounds");
        }
        repository.findByCell(project.getId(), row, col).ifPresent(occupant -> {
            if (excludingPlotId == null || !occupant.getId().equals(excludingPlotId)) {
                throw new ConflictException("error.plot.cellOccupied");
            }
        });
    }

    private String normalize(String plotNumber) {
        return NON_ALNUM.matcher(plotNumber).replaceAll("").toUpperCase();
    }

    @SuppressWarnings("unchecked")
    private List<int[]> readBlockedCells(String gridLayoutJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, List<String>> map = mapper.readValue(gridLayoutJson, Map.class);
            List<String> blocked = map.getOrDefault("blocked", List.of());
            List<int[]> result = new ArrayList<>();
            for (String cell : blocked) {
                int cIndex = cell.indexOf('c');
                result.add(new int[] { Integer.parseInt(cell.substring(1, cIndex)), Integer.parseInt(cell.substring(cIndex + 1)) });
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private PlotResponse toResponse(Plot p) {
        return new PlotResponse(p.getId(), p.getProjectId(), p.getPlotNumber(), p.getStatus().name(),
                p.getReservedFor(), p.getReservedUntil(), p.getSizeValue(), p.getSizeUnit(), p.getSizeSqft(),
                p.getFacing() == null ? null : p.getFacing().name(), p.getPrice(), p.getPricePerUnit(),
                p.isGarden(), p.isCorner(), p.isHot(), p.getRemarks(), p.getGridRow(), p.getGridCol(), p.getCurrentSaleId());
    }
}
