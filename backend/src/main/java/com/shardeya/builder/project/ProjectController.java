package com.shardeya.builder.project;

import com.shardeya.builder.project.dto.GridConfigRequest;
import com.shardeya.builder.project.dto.GridConfigResponse;
import com.shardeya.builder.project.dto.ProjectCreateRequest;
import com.shardeya.builder.project.dto.ProjectDetailResponse;
import com.shardeya.builder.project.dto.ProjectMediaAttachRequest;
import com.shardeya.builder.project.dto.ProjectResponse;
import com.shardeya.builder.project.dto.ProjectStatusUpdateRequest;
import com.shardeya.builder.project.dto.ProjectUpdateRequest;
import com.shardeya.platform.CursorPage;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    public CursorPage<ProjectResponse> list(@RequestParam(required = false) String cursor,
                                             @RequestParam(defaultValue = "25") int limit) {
        return service.list(cursor, limit);
    }

    @PostMapping
    @RequiresPermission("PROJECT_CREATE")
    public ResponseEntity<ProjectDetailResponse> create(@Valid @RequestBody ProjectCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    public ProjectDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/summary")
    public ProjectDetailResponse summary(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    @RequiresPermission("PROJECT_EDIT")
    public ProjectDetailResponse update(@PathVariable UUID id, @Valid @RequestBody ProjectUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @RequiresPermission("PROJECT_EDIT")
    public ProjectDetailResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody ProjectStatusUpdateRequest request) {
        service.updateStatus(id, request.status());
        return service.get(id);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("PROJECT_DELETE")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @RequiresPermission("PROJECT_DELETE")
    public ProjectDetailResponse restore(@PathVariable UUID id) {
        service.restore(id);
        return service.get(id);
    }

    @GetMapping("/{id}/grid-config")
    public GridConfigResponse gridConfig(@PathVariable UUID id) {
        return service.getGridConfig(id);
    }

    @PutMapping("/{id}/grid-config")
    @RequiresPermission("PROJECT_EDIT")
    public GridConfigResponse putGridConfig(@PathVariable UUID id, @Valid @RequestBody GridConfigRequest request) {
        return service.putGridConfig(id, request);
    }

    @GetMapping("/{id}/media")
    public java.util.List<com.shardeya.builder.project.dto.ProjectMediaResponse> listMedia(
            @PathVariable UUID id, @RequestParam(required = false) String role) {
        return service.listMedia(id, role);
    }

    @PostMapping("/{id}/media")
    @RequiresPermission("PROJECT_EDIT")
    public ResponseEntity<Void> attachMedia(@PathVariable UUID id, @Valid @RequestBody ProjectMediaAttachRequest request) {
        service.attachMedia(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/media/{mediaId}")
    @RequiresPermission("PROJECT_EDIT")
    public ResponseEntity<Void> detachMedia(@PathVariable UUID id, @PathVariable UUID mediaId) {
        service.detachMedia(id, mediaId);
        return ResponseEntity.noContent().build();
    }
}
