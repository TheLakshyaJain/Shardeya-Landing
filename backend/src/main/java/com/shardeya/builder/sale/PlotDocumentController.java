package com.shardeya.builder.sale;

import com.shardeya.builder.sale.dto.PlotDocumentAttachRequest;
import com.shardeya.builder.sale.dto.PlotDocumentResponse;
import com.shardeya.platform.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class PlotDocumentController {

    private final PlotDocumentService service;

    public PlotDocumentController(PlotDocumentService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/sales/{id}/documents")
    public List<PlotDocumentResponse> list(@PathVariable UUID id) {
        return service.list(id);
    }

    @PostMapping("/api/v1/sales/{id}/documents")
    @RequiresPermission("DATA_EDIT_ALL")
    public ResponseEntity<PlotDocumentResponse> attach(@PathVariable UUID id, @Valid @RequestBody PlotDocumentAttachRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.attach(id, request));
    }

    @DeleteMapping("/api/v1/sales/documents/{id}")
    @RequiresPermission("DATA_EDIT_ALL")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
