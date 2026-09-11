package com.shardeya.builder.document;

import com.shardeya.builder.document.dto.CloneTemplateRequest;
import com.shardeya.builder.document.dto.DocumentTemplateResponse;
import com.shardeya.builder.document.dto.DocumentTemplateUpsertRequest;
import com.shardeya.builder.document.dto.TemplatePreviewRequest;
import com.shardeya.builder.document.dto.TemplatePreviewResponse;
import com.shardeya.builder.document.dto.VariablePaletteResponse;
import com.shardeya.platform.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** B-11 §4/§9: template editing is DOCUMENT_TEMPLATE_EDIT (Admin only, per the seeded role catalogue). */
@RestController
public class DocumentTemplateController {

    private final DocumentTemplateService service;

    public DocumentTemplateController(DocumentTemplateService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/documents/templates")
    @RequiresPermission("DOCUMENT_TEMPLATE_EDIT")
    public List<DocumentTemplateResponse> list() {
        return service.list();
    }

    @GetMapping("/api/v1/documents/templates/{id}")
    @RequiresPermission("DOCUMENT_TEMPLATE_EDIT")
    public DocumentTemplateResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/api/v1/documents/templates/variables")
    @RequiresPermission("DOCUMENT_TEMPLATE_EDIT")
    public VariablePaletteResponse variables(@RequestParam String docType) {
        return service.variablePalette(docType);
    }

    @PostMapping("/api/v1/documents/templates")
    @RequiresPermission("DOCUMENT_TEMPLATE_EDIT")
    public DocumentTemplateResponse clone_(@Valid @RequestBody CloneTemplateRequest req) {
        return service.clone_(req);
    }

    @PutMapping("/api/v1/documents/templates/{id}")
    @RequiresPermission("DOCUMENT_TEMPLATE_EDIT")
    public DocumentTemplateResponse update(@PathVariable UUID id, @Valid @RequestBody DocumentTemplateUpsertRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/api/v1/documents/templates/{id}/preview")
    @RequiresPermission("DOCUMENT_TEMPLATE_EDIT")
    public TemplatePreviewResponse preview(@PathVariable UUID id, @Valid @RequestBody TemplatePreviewRequest req) {
        return service.preview(id, req);
    }

    @PostMapping("/api/v1/documents/templates/{id}/activate")
    @RequiresPermission("DOCUMENT_TEMPLATE_EDIT")
    public DocumentTemplateResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }
}
