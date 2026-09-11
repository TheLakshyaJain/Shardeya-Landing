package com.shardeya.builder.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.dto.ApprovalTag;
import com.shardeya.builder.project.dto.GridCell;
import com.shardeya.builder.project.dto.GridConfigRequest;
import com.shardeya.builder.project.dto.GridConfigResponse;
import com.shardeya.builder.project.dto.PlotStatusCounts;
import com.shardeya.builder.project.dto.ProjectCreateRequest;
import com.shardeya.builder.project.dto.ProjectDetailResponse;
import com.shardeya.builder.project.dto.ProjectMediaAttachRequest;
import com.shardeya.builder.project.dto.ProjectResponse;
import com.shardeya.builder.project.dto.ProjectUpdateRequest;
import com.shardeya.foundation.calculator.AreaConversionService;
import com.shardeya.foundation.subscription.EntitlementService;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.Cursor;
import com.shardeya.platform.CursorPage;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.shared.AreaMeasure;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository repository;
    private final ProjectMediaRepository mediaRepository;
    private final PlotRepository plotRepository;
    private final AreaConversionService areaConversionService;
    private final EntitlementService entitlementService;
    private final ProjectAccessGuard accessGuard;
    private final ObjectMapper objectMapper;
    private final TenantContextBinder tenantContextBinder;

    public ProjectService(ProjectRepository repository, ProjectMediaRepository mediaRepository,
                           PlotRepository plotRepository, AreaConversionService areaConversionService,
                           EntitlementService entitlementService, ProjectAccessGuard accessGuard,
                           ObjectMapper objectMapper, TenantContextBinder tenantContextBinder) {
        this.repository = repository;
        this.mediaRepository = mediaRepository;
        this.plotRepository = plotRepository;
        this.areaConversionService = areaConversionService;
        this.entitlementService = entitlementService;
        this.accessGuard = accessGuard;
        this.objectMapper = objectMapper;
        this.tenantContextBinder = tenantContextBinder;
    }

    @Transactional
    public ProjectDetailResponse create(ProjectCreateRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();

        if (repository.existsByOrgIdAndDeletedAtIsNullAndNameIgnoreCase(orgId, req.name())) {
            throw new ConflictException("error.project.nameTaken");
        }
        entitlementService.assertWithinQuota(orgId, "BUILDER_PROJECTS", null, "error.project.quotaExceeded");

        AreaMeasure area = areaConversionService.toSqft(req.totalAreaValue(), req.totalAreaUnit(), req.stateCode());

        Project project = new Project(UUID.randomUUID(), orgId, req.name(), req.projectType(), req.address(),
                req.locality(), req.city(), req.stateCode(), area.value(), area.unit(), area.sqft(),
                req.declaredPlotCount());
        if (req.status() != null) {
            project.setStatus(req.status());
        }
        project.setPincode(req.pincode());
        project.setGoogleMapsUrl(req.googleMapsUrl());
        project.setLatitude(req.latitude());
        project.setLongitude(req.longitude());
        project.setLaunchDate(req.launchDate());
        project.setExpectedCompletionDate(req.expectedCompletionDate());
        project.setDescription(req.description());
        project.setApprovals(writeApprovals(req.approvals()));
        project.setReraNumber(req.reraNumber());
        project.setCoverMediaId(req.coverMediaId());
        project.setLayoutMediaId(req.layoutMediaId());
        project.setBrochureMediaId(req.brochureMediaId());

        repository.save(project);
        return toDetail(project);
    }

    public ProjectDetailResponse get(UUID id) {
        Project project = repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(project.getId());
        return toDetail(project);
    }

    public CursorPage<ProjectResponse> list(String cursorRaw, int limit) {
        UUID orgId = tenantContextBinder.currentOrgId();
        var tenant = tenantContextBinder.current();
        Cursor cursor = Cursor.decode(cursorRaw);
        int fetchSize = Math.min(Math.max(limit, 1), 100) + 1;
        List<Project> fetched;
        if (tenant.allProjects()) {
            fetched = cursor == null
                    ? repository.findFirstPage(orgId, PageRequest.of(0, fetchSize))
                    : repository.findPageAfter(orgId, cursor.createdAt(), cursor.id(), PageRequest.of(0, fetchSize));
        } else if (tenant.projectScope().isEmpty()) {
            fetched = List.of();
        } else {
            fetched = cursor == null
                    ? repository.findFirstPageScoped(orgId, tenant.projectScope(), PageRequest.of(0, fetchSize))
                    : repository.findPageAfterScoped(orgId, cursor.createdAt(), cursor.id(), tenant.projectScope(),
                            PageRequest.of(0, fetchSize));
        }

        CursorPage<Project> page = CursorPage.of(fetched, Math.min(Math.max(limit, 1), 100),
                p -> new Cursor(p.getCreatedAt(), p.getId()));

        Map<UUID, PlotStatusCounts> counts = countsFor(page.items().stream().map(Project::getId).toList());
        List<ProjectResponse> items = page.items().stream()
                .map(p -> toSummary(p, counts.getOrDefault(p.getId(), PlotStatusCounts.EMPTY)))
                .toList();
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    @Transactional
    public ProjectDetailResponse update(UUID id, ProjectUpdateRequest req) {
        Project project = repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(project.getId());

        if (req.name() != null && !req.name().equalsIgnoreCase(project.getName())) {
            if (repository.existsByOrgIdAndDeletedAtIsNullAndNameIgnoreCase(project.getOrgId(), req.name())) {
                throw new ConflictException("error.project.nameTaken");
            }
            project.setName(req.name());
        }
        if (req.projectType() != null) project.setProjectType(req.projectType());
        if (req.status() != null) project.setStatus(req.status());
        if (req.address() != null) project.setAddress(req.address());
        if (req.locality() != null) project.setLocality(req.locality());
        if (req.city() != null) project.setCity(req.city());
        if (req.stateCode() != null) project.setStateCode(req.stateCode());
        if (req.pincode() != null) project.setPincode(req.pincode());
        if (req.googleMapsUrl() != null) project.setGoogleMapsUrl(req.googleMapsUrl());
        if (req.latitude() != null) project.setLatitude(req.latitude());
        if (req.longitude() != null) project.setLongitude(req.longitude());
        if (req.totalAreaValue() != null && req.totalAreaUnit() != null) {
            AreaMeasure area = areaConversionService.toSqft(req.totalAreaValue(), req.totalAreaUnit(), project.getStateCode());
            project.setTotalAreaValue(area.value());
            project.setTotalAreaUnit(area.unit());
            project.setTotalAreaSqft(area.sqft());
        }
        if (req.declaredPlotCount() != null) project.setDeclaredPlotCount(req.declaredPlotCount());
        if (req.launchDate() != null) project.setLaunchDate(req.launchDate());
        if (req.expectedCompletionDate() != null) project.setExpectedCompletionDate(req.expectedCompletionDate());
        if (req.description() != null) project.setDescription(req.description());
        if (req.approvals() != null) project.setApprovals(writeApprovals(req.approvals()));
        if (req.reraNumber() != null) project.setReraNumber(req.reraNumber());
        if (req.coverMediaId() != null) project.setCoverMediaId(req.coverMediaId());
        if (req.layoutMediaId() != null) project.setLayoutMediaId(req.layoutMediaId());
        if (req.brochureMediaId() != null) project.setBrochureMediaId(req.brochureMediaId());

        repository.save(project);
        return toDetail(project);
    }

    @Transactional
    public void updateStatus(UUID id, Project.Status status) {
        Project project = repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(project.getId());
        project.setStatus(status);
        repository.save(project);
    }

    // Deletion guard (B-02 §7): blocked if any plot has a non-cancelled sale.
    // plot_sale doesn't exist until M3, so that check is a no-op today —
    // deleting a project with plots but no sales cascades the soft-delete to
    // every plot (B-02 §10: "plots soft-delete with it and restore together").
    @Transactional
    public void delete(UUID id) {
        Project project = repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(project.getId());

        Instant now = Instant.now();
        UUID userId = tenantContextBinder.current().userId();
        for (Plot plot : plotRepository.findByProjectIdAndDeletedAtIsNull(project.getId())) {
            plot.setDeletedAt(now);
            plotRepository.save(plot);
        }
        project.setDeletedAt(now);
        repository.save(project);
    }

    @Transactional
    public void restore(UUID id) {
        Project project = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(project.getId());
        project.setDeletedAt(null);
        repository.save(project);
        for (Plot plot : plotRepository.findByProjectId(project.getId())) {
            if (plot.getDeletedAt() != null) {
                plot.setDeletedAt(null);
                plotRepository.save(plot);
            }
        }
    }

    public GridConfigResponse getGridConfig(UUID id) {
        Project project = repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(project.getId());
        return new GridConfigResponse(project.getGridRows(), project.getGridCols(), readBlocked(project.getGridLayout()));
    }

    // Resizing never destroys plot data (B-02 §7): plots that fall outside
    // the new bounds become unplaced (grid_row/grid_col -> NULL), not deleted.
    @Transactional
    public GridConfigResponse putGridConfig(UUID id, GridConfigRequest req) {
        Project project = repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(project.getId());

        project.setGridRows(req.rows());
        project.setGridCols(req.cols());
        try {
            project.setGridLayout(objectMapper.writeValueAsString(Map.of("blocked",
                    req.blockedCells() == null ? List.of() : req.blockedCells().stream()
                            .map(c -> "r" + c.row() + "c" + c.col()).toList())));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        for (Plot plot : plotRepository.findByProjectIdAndDeletedAtIsNull(project.getId())) {
            if (plot.getGridRow() != null && (plot.getGridRow() >= req.rows() || plot.getGridCol() >= req.cols())) {
                plot.setGridRow(null);
                plot.setGridCol(null);
                plotRepository.save(plot);
            }
        }

        repository.save(project);
        return new GridConfigResponse(project.getGridRows(), project.getGridCols(), req.blockedCells());
    }

    @Transactional
    public void attachMedia(UUID projectId, ProjectMediaAttachRequest req) {
        Project project = repository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(project.getId());

        ProjectMedia media = new ProjectMedia(UUID.randomUUID(), project.getOrgId(), project.getId(),
                req.mediaId(), req.role(), req.sortOrder());
        mediaRepository.save(media);

        switch (req.role()) {
            case "COVER" -> project.setCoverMediaId(req.mediaId());
            case "LAYOUT" -> project.setLayoutMediaId(req.mediaId());
            case "BROCHURE" -> project.setBrochureMediaId(req.mediaId());
            default -> { /* GALLERY: tracked only in project_media */ }
        }
        repository.save(project);
    }

    public List<com.shardeya.builder.project.dto.ProjectMediaResponse> listMedia(UUID projectId, String role) {
        Project project = repository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(project.getId());

        return mediaRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrder(project.getId()).stream()
                .filter(m -> role == null || role.equals(m.getRole()))
                .map(m -> new com.shardeya.builder.project.dto.ProjectMediaResponse(m.getMediaId(), m.getRole(), m.getSortOrder()))
                .toList();
    }

    @Transactional
    public void detachMedia(UUID projectId, UUID mediaId) {
        Project project = repository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(project.getId());

        mediaRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrder(project.getId()).stream()
                .filter(m -> m.getMediaId().equals(mediaId))
                .forEach(m -> {
                    m.setDeletedAt(Instant.now());
                    mediaRepository.save(m);
                });

        if (mediaId.equals(project.getCoverMediaId())) project.setCoverMediaId(null);
        if (mediaId.equals(project.getLayoutMediaId())) project.setLayoutMediaId(null);
        if (mediaId.equals(project.getBrochureMediaId())) project.setBrochureMediaId(null);
        repository.save(project);
    }

    private Map<UUID, PlotStatusCounts> countsFor(List<UUID> projectIds) {
        if (projectIds.isEmpty()) return Map.of();
        Map<UUID, Map<Plot.Status, Long>> byProject = plotRepository.countByStatusForProjects(projectIds).stream()
                .collect(Collectors.groupingBy(PlotRepository.StatusCount::getProjectId,
                        Collectors.toMap(PlotRepository.StatusCount::getStatus, PlotRepository.StatusCount::getCnt)));
        Map<UUID, PlotStatusCounts> result = new HashMap<>();
        for (UUID id : projectIds) {
            Map<Plot.Status, Long> statuses = byProject.getOrDefault(id, Map.of());
            long available = statuses.getOrDefault(Plot.Status.AVAILABLE, 0L);
            long reserved = statuses.getOrDefault(Plot.Status.RESERVED, 0L);
            long sold = statuses.getOrDefault(Plot.Status.SOLD, 0L);
            result.put(id, new PlotStatusCounts(available + reserved + sold, available, reserved, sold));
        }
        return result;
    }

    private ProjectResponse toSummary(Project p, PlotStatusCounts counts) {
        return new ProjectResponse(p.getId(), p.getName(), p.getProjectType().name(), p.getStatus().name(),
                p.getCity(), p.getLocality(), p.getCoverMediaId(), counts);
    }

    private ProjectDetailResponse toDetail(Project p) {
        PlotStatusCounts counts = countsFor(List.of(p.getId())).getOrDefault(p.getId(), PlotStatusCounts.EMPTY);
        long delta = p.getDeclaredPlotCount() - counts.total();
        return new ProjectDetailResponse(p.getId(), p.getName(), p.getProjectType().name(), p.getStatus().name(),
                p.getAddress(), p.getLocality(), p.getCity(), p.getStateCode(), p.getPincode(), p.getGoogleMapsUrl(),
                p.getLatitude(), p.getLongitude(),
                p.getTotalAreaValue(), p.getTotalAreaUnit(), p.getTotalAreaSqft(), p.getDeclaredPlotCount(),
                p.getLaunchDate(), p.getExpectedCompletionDate(), p.getDescription(), readApprovals(p.getApprovals()),
                p.getReraNumber(), p.getCoverMediaId(), p.getLayoutMediaId(), p.getBrochureMediaId(),
                p.getGridRows(), p.getGridCols(), counts, delta);
    }

    private String writeApprovals(List<ApprovalTag> approvals) {
        try {
            return objectMapper.writeValueAsString(approvals == null ? List.of() : approvals);
        } catch (Exception e) {
            throw new BadRequestException("approvals", "APPROVALS_INVALID", "error.project.approvalsInvalid");
        }
    }

    private List<ApprovalTag> readApprovals(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ApprovalTag>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<GridCell> readBlocked(String gridLayoutJson) {
        try {
            Map<String, List<String>> map = objectMapper.readValue(gridLayoutJson, new TypeReference<Map<String, List<String>>>() { });
            List<String> blocked = map.getOrDefault("blocked", List.of());
            return blocked.stream().map(this::parseCell).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private GridCell parseCell(String encoded) {
        // "r{row}c{col}"
        int cIndex = encoded.indexOf('c');
        int row = Integer.parseInt(encoded.substring(1, cIndex));
        int col = Integer.parseInt(encoded.substring(cIndex + 1));
        return new GridCell(row, col);
    }
}
