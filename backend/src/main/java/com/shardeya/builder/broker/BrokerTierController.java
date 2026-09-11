package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.BrokerTierCreateRequest;
import com.shardeya.builder.broker.dto.BrokerTierResponse;
import com.shardeya.builder.broker.dto.TierOverrideRequest;
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
import java.util.Map;
import java.util.UUID;

@RestController
public class BrokerTierController {

    private final BrokerTierService service;

    public BrokerTierController(BrokerTierService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/broker-tiers")
    @RequiresPermission("BROKER_VIEW")
    public List<BrokerTierResponse> list() {
        return service.list();
    }

    @PostMapping("/api/v1/broker-tiers")
    @RequiresPermission("BROKER_MANAGE")
    public BrokerTierResponse create(@Valid @RequestBody BrokerTierCreateRequest req) {
        return service.create(req);
    }

    @PatchMapping("/api/v1/broker-tiers/{id}")
    @RequiresPermission("BROKER_MANAGE")
    public BrokerTierResponse update(@PathVariable UUID id, @Valid @RequestBody BrokerTierCreateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/api/v1/broker-tiers/{id}")
    @RequiresPermission("BROKER_MANAGE")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/brokers/{id}/tier-override")
    @RequiresPermission("BROKER_MANAGE")
    public ResponseEntity<Void> override(@PathVariable UUID id, @Valid @RequestBody TierOverrideRequest req) {
        service.override(id, req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/broker-tiers/recalculate")
    @RequiresPermission("BROKER_MANAGE")
    public Map<String, Integer> recalculate() {
        return Map.of("evaluated", service.recalculateAll());
    }
}
