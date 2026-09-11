package com.shardeya.foundation.importexport;

import com.shardeya.foundation.importexport.dto.ReportDefinitionSummary;
import com.shardeya.foundation.importexport.dto.ReportExportRequest;
import com.shardeya.foundation.importexport.dto.ReportPreviewRequest;
import com.shardeya.foundation.importexport.dto.ReportPreviewResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * M-10 §4/§9. No blanket {@code @RequiresPermission} here -- each report's
 * own {@code required_permission} is data-driven, checked inside
 * {@link ReportService} against the specific code, not something a single
 * controller-level annotation could express.
 */
@RestController
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/reports")
    public java.util.List<ReportDefinitionSummary> list() {
        return service.list();
    }

    @PostMapping("/api/v1/reports/{code}/preview")
    public ReportPreviewResponse preview(@PathVariable String code, @Valid @RequestBody(required = false) ReportPreviewRequest req) {
        ReportPreviewRequest safe = req == null ? new ReportPreviewRequest(java.util.Map.of(), 0) : req;
        return service.preview(code, safe.filtersOrEmpty(), safe.pageOrDefault(), 50);
    }

    @PostMapping("/api/v1/reports/{code}/export")
    public ResponseEntity<byte[]> export(@PathVariable String code, @Valid @RequestBody ReportExportRequest req) {
        byte[] bytes = service.export(code, req.filtersOrEmpty(), req.format());
        MediaType contentType = switch (req.format()) {
            case "XLSX" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "CSV" -> MediaType.valueOf("text/csv");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        String ext = req.format().equals("XLSX") ? "xlsx" : "csv";
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + code.toLowerCase() + "." + ext + "\"")
                .body(bytes);
    }
}
