package com.shardeya.builder.document;

import com.shardeya.builder.document.dto.BulkGenerateRequest;
import com.shardeya.builder.document.dto.GenerateDocumentRequest;
import com.shardeya.builder.document.dto.GeneratedDocumentResponse;
import com.shardeya.platform.IdempotencyService;
import com.shardeya.platform.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** B-11 §4/§9: generation is DOCUMENT_GENERATE (Admin, Manager; Accounts Staff limited to receipts, enforced in the service). */
@RestController
public class DocumentController {

    private final DocumentGenerationService service;
    private final IdempotencyService idempotencyService;

    public DocumentController(DocumentGenerationService service, IdempotencyService idempotencyService) {
        this.service = service;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/api/v1/documents/generate")
    @RequiresPermission("DOCUMENT_GENERATE")
    public GeneratedDocumentResponse generate(@Valid @RequestBody GenerateDocumentRequest req,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // B-11 §10 "concurrent generation of the same receipt -> idempotency
        // key returns the existing document" -- same generic mechanism
        // PaymentController already uses for exactly this shape.
        return idempotencyService.withIdempotency("document-generate", idempotencyKey, GeneratedDocumentResponse.class,
                () -> service.generate(req));
    }

    @PostMapping("/api/v1/documents/bulk-generate")
    @RequiresPermission("DOCUMENT_GENERATE")
    public ResponseEntity<byte[]> bulkGenerate(@Valid @RequestBody BulkGenerateRequest req) {
        byte[] zip = service.bulkGenerateDemandLetters(req);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"demand-letters.zip\"")
                .body(zip);
    }

    @GetMapping("/api/v1/documents/{id}")
    @RequiresPermission("DOCUMENT_GENERATE")
    public GeneratedDocumentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/api/v1/documents/{id}/download")
    @RequiresPermission("DOCUMENT_GENERATE")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        GeneratedDocumentResponse meta = service.get(id);
        byte[] pdf = service.downloadBytes(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.documentNumber().replace('/', '-') + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/api/v1/documents")
    @RequiresPermission("DOCUMENT_GENERATE")
    public List<GeneratedDocumentResponse> listForEntity(@RequestParam String entityType, @RequestParam UUID entityId) {
        return service.listForEntity(entityType, entityId);
    }
}
