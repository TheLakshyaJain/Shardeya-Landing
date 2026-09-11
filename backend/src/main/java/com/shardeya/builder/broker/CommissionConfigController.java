package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.CommissionConfigCreateRequest;
import com.shardeya.builder.broker.dto.CommissionConfigResponse;
import com.shardeya.builder.broker.dto.CommissionPreviewRequest;
import com.shardeya.builder.broker.dto.CommissionPreviewResponse;
import com.shardeya.platform.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class CommissionConfigController {

    private final CommissionConfigService service;

    public CommissionConfigController(CommissionConfigService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/brokers/{id}/commission-configs")
    @RequiresPermission("BROKER_VIEW")
    public List<CommissionConfigResponse> list(@PathVariable UUID id) {
        return service.list(id);
    }

    @PostMapping("/api/v1/brokers/{id}/commission-configs")
    @RequiresPermission("BROKER_MANAGE")
    public CommissionConfigResponse create(@PathVariable UUID id, @Valid @RequestBody CommissionConfigCreateRequest req) {
        return service.create(id, req);
    }

    @PatchMapping("/api/v1/commission-configs/{id}")
    @RequiresPermission("BROKER_MANAGE")
    public CommissionConfigResponse update(@PathVariable UUID id, @Valid @RequestBody CommissionConfigCreateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/api/v1/commission-configs/{id}")
    @RequiresPermission("BROKER_MANAGE")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/brokers/{id}/commission-preview")
    @RequiresPermission("BROKER_VIEW")
    public CommissionPreviewResponse preview(@PathVariable UUID id, @Valid @RequestBody CommissionPreviewRequest req) {
        return service.preview(id, req);
    }
}
