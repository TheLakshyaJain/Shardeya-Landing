package com.shardeya.foundation.importexport;

import com.shardeya.foundation.importexport.dto.ImportCommitRequest;
import com.shardeya.foundation.importexport.dto.ImportJobResponse;
import com.shardeya.foundation.importexport.dto.ImportRowResponse;
import com.shardeya.foundation.importexport.dto.PlotImportStartRequest;
import com.shardeya.platform.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ImportController {

    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
    }

    @GetMapping("/projects/{projectId}/plots/import/template")
    @RequiresPermission("PLOT_BULK_UPLOAD")
    public ResponseEntity<byte[]> template(@PathVariable UUID projectId) {
        byte[] file = service.generateTemplate(projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shardeya-plot-import-template.xlsx\"")
                .body(file);
    }

    @PostMapping("/projects/{projectId}/plots/import")
    @RequiresPermission("PLOT_BULK_UPLOAD")
    public ImportJobResponse start(@PathVariable UUID projectId, @Valid @RequestBody PlotImportStartRequest request) {
        return service.start(projectId, request);
    }

    @GetMapping("/import/jobs/{id}")
    public ImportJobResponse status(@PathVariable UUID id) {
        return service.status(id);
    }

    @GetMapping("/import/jobs/{id}/rows")
    public List<ImportRowResponse> rows(@PathVariable UUID id, @RequestParam(required = false) ImportRow.Status status) {
        return service.rows(id, status);
    }

    @PatchMapping("/import/jobs/{id}/rows/{rowId}")
    @RequiresPermission("PLOT_BULK_UPLOAD")
    public ImportRowResponse patchRow(@PathVariable UUID id, @PathVariable UUID rowId, @RequestBody Map<String, String> data) {
        return service.patchRow(id, rowId, data);
    }

    @PostMapping("/import/jobs/{id}/commit")
    @RequiresPermission("PLOT_BULK_UPLOAD")
    public ImportJobResponse commit(@PathVariable UUID id, @RequestBody ImportCommitRequest request) {
        return service.commit(id, request);
    }

    @PostMapping("/import/jobs/{id}/cancel")
    @RequiresPermission("PLOT_BULK_UPLOAD")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
