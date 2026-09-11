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
import com.shardeya.builder.plot.dto.QuickCreateCommitResponse;
import com.shardeya.builder.plot.dto.QuickCreatePreviewResponse;
import com.shardeya.builder.plot.dto.QuickCreateRequest;
import com.shardeya.platform.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PlotController {

    private final PlotService service;
    private final PlotQuickCreateService quickCreateService;

    public PlotController(PlotService service, PlotQuickCreateService quickCreateService) {
        this.service = service;
        this.quickCreateService = quickCreateService;
    }

    @GetMapping("/projects/{projectId}/plots/grid")
    public GridResponse grid(@PathVariable UUID projectId) {
        return service.grid(projectId);
    }

    @GetMapping("/projects/{projectId}/plots/stats")
    public PlotStatsResponse stats(@PathVariable UUID projectId) {
        return service.stats(projectId);
    }

    @GetMapping("/projects/{projectId}/plots")
    public List<PlotResponse> list(@PathVariable UUID projectId,
                                    @RequestParam(required = false) Plot.Status status,
                                    @RequestParam(required = false) Plot.Facing facing,
                                    @RequestParam(required = false) BigDecimal sizeMin,
                                    @RequestParam(required = false) BigDecimal sizeMax,
                                    @RequestParam(required = false) BigDecimal priceMin,
                                    @RequestParam(required = false) BigDecimal priceMax,
                                    @RequestParam(required = false) Boolean isHot,
                                    @RequestParam(required = false) Boolean isCorner,
                                    @RequestParam(required = false) Boolean isGarden,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(defaultValue = "500") int limit) {
        return service.list(projectId, status, facing, sizeMin, sizeMax, priceMin, priceMax, isHot, isCorner,
                isGarden, search, limit);
    }

    @PostMapping("/projects/{projectId}/plots")
    @RequiresPermission("PLOT_CREATE")
    public ResponseEntity<PlotResponse> create(@PathVariable UUID projectId, @Valid @RequestBody PlotCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, request));
    }

    // B-06 Path A: same PLOT_CREATE permission as manual single-plot create,
    // no PLOT_BULK_UPLOAD and no entitlement gate -- unlike Path B (Excel
    // import), Quick Range Create is available on every plan.
    @PostMapping("/projects/{projectId}/plots/quick-create")
    @RequiresPermission("PLOT_CREATE")
    public QuickCreatePreviewResponse quickCreatePreview(@PathVariable UUID projectId, @Valid @RequestBody QuickCreateRequest request) {
        return quickCreateService.preview(projectId, request);
    }

    @PostMapping("/projects/{projectId}/plots/quick-create/commit")
    @RequiresPermission("PLOT_CREATE")
    public QuickCreateCommitResponse quickCreateCommit(@PathVariable UUID projectId, @Valid @RequestBody QuickCreateRequest request) {
        return quickCreateService.commit(projectId, request);
    }

    @GetMapping("/plots/{id}")
    public PlotResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/plots/{id}")
    @RequiresPermission("PLOT_EDIT")
    public PlotResponse update(@PathVariable UUID id, @Valid @RequestBody PlotUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/plots/{id}/status")
    @RequiresPermission("PLOT_EDIT")
    public PlotResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody PlotStatusUpdateRequest request) {
        return service.updateStatus(id, request);
    }

    @PatchMapping("/plots/{id}/hot")
    @RequiresPermission("PLOT_EDIT")
    public PlotResponse updateHot(@PathVariable UUID id, @Valid @RequestBody PlotHotRequest request) {
        return service.updateHot(id, request);
    }

    @PutMapping("/plots/{id}/position")
    @RequiresPermission("PLOT_EDIT")
    public PlotResponse updatePosition(@PathVariable UUID id, @Valid @RequestBody PlotPositionRequest request) {
        return service.updatePosition(id, request);
    }

    @DeleteMapping("/plots/{id}")
    @RequiresPermission("PLOT_DELETE")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{projectId}/plots/bulk-position")
    @RequiresPermission("PLOT_EDIT")
    public ResponseEntity<Void> bulkPosition(@PathVariable UUID projectId, @RequestBody BulkPositionRequest request) {
        service.bulkPosition(projectId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{projectId}/plots/bulk-update")
    @RequiresPermission("PLOT_EDIT")
    public Map<String, Integer> bulkUpdate(@PathVariable UUID projectId, @RequestBody BulkUpdateRequest request) {
        return Map.of("updated", service.bulkUpdate(projectId, request));
    }
}
