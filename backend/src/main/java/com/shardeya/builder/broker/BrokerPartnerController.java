package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.BankDetailsResponse;
import com.shardeya.builder.broker.dto.BrokerCreateRequest;
import com.shardeya.builder.broker.dto.BrokerDealResponse;
import com.shardeya.builder.broker.dto.BrokerInteractionCreateRequest;
import com.shardeya.builder.broker.dto.BrokerInteractionResponse;
import com.shardeya.builder.broker.dto.BrokerNetworkNodeResponse;
import com.shardeya.builder.broker.dto.BrokerPerformanceResponse;
import com.shardeya.builder.broker.dto.BrokerResponse;
import com.shardeya.builder.broker.dto.BrokerUpdateRequest;
import com.shardeya.builder.broker.dto.DesignationOverrideRequest;
import com.shardeya.platform.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class BrokerPartnerController {

    private final BrokerPartnerService service;
    private final BrokerInteractionService interactionService;
    private final DesignationPromotionService designationPromotionService;

    public BrokerPartnerController(BrokerPartnerService service, BrokerInteractionService interactionService,
                                    DesignationPromotionService designationPromotionService) {
        this.service = service;
        this.interactionService = interactionService;
        this.designationPromotionService = designationPromotionService;
    }

    @GetMapping("/api/v1/brokers")
    @RequiresPermission("BROKER_VIEW")
    public List<BrokerResponse> list(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) UUID tierId,
                                      @RequestParam(required = false) String search) {
        return service.list(status, tierId, search);
    }

    @PostMapping("/api/v1/brokers")
    @RequiresPermission("BROKER_MANAGE")
    public BrokerResponse create(@Valid @RequestBody BrokerCreateRequest req) {
        return service.create(req);
    }

    // 06-BROKER-NETWORK-ENGINE.md §28. A literal path segment ("network")
    // always wins over {id} in Spring's path matching regardless of
    // declaration order, so this is safe placed anywhere relative to the
    // GET /{id} mapping below -- kept here for readability, next to list().
    @GetMapping("/api/v1/brokers/network")
    @RequiresPermission("BROKER_VIEW")
    public List<BrokerNetworkNodeResponse> network() {
        return service.listNetwork();
    }

    @GetMapping("/api/v1/brokers/{id}")
    @RequiresPermission("BROKER_VIEW")
    public BrokerResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/api/v1/brokers/{id}")
    @RequiresPermission("BROKER_MANAGE")
    public BrokerResponse update(@PathVariable UUID id, @Valid @RequestBody BrokerUpdateRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/api/v1/brokers/{id}/deactivate")
    @RequiresPermission("BROKER_MANAGE")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/brokers/{id}/reactivate")
    @RequiresPermission("BROKER_MANAGE")
    public ResponseEntity<Void> reactivate(@PathVariable UUID id) {
        service.reactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/brokers/{id}/block")
    @RequiresPermission("BROKER_MANAGE")
    public ResponseEntity<Void> block(@PathVariable UUID id) {
        service.block(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/brokers/{id}")
    @RequiresPermission("BROKER_MANAGE")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/brokers/{id}/bank-details")
    @RequiresPermission("SENSITIVE_VIEW")
    public BankDetailsResponse bankDetails(@PathVariable UUID id, @RequestParam(required = false) String reason) {
        return service.bankDetails(id, reason != null ? reason : "Viewing bank details");
    }

    @GetMapping("/api/v1/brokers/{id}/performance")
    @RequiresPermission("BROKER_VIEW")
    public BrokerPerformanceResponse performance(@PathVariable UUID id) {
        return service.performance(id);
    }

    @GetMapping("/api/v1/brokers/{id}/deals")
    @RequiresPermission("BROKER_VIEW")
    public List<BrokerDealResponse> deals(@PathVariable UUID id) {
        return service.deals(id);
    }

    @GetMapping("/api/v1/brokers/{id}/interactions")
    @RequiresPermission("BROKER_VIEW")
    public List<BrokerInteractionResponse> interactions(@PathVariable UUID id) {
        return interactionService.list(id);
    }

    @PostMapping("/api/v1/brokers/{id}/interactions")
    @RequiresPermission("BROKER_MANAGE")
    public BrokerInteractionResponse addInteraction(@PathVariable UUID id, @Valid @RequestBody BrokerInteractionCreateRequest req) {
        return interactionService.create(id, req);
    }

    // 06-BROKER-NETWORK-ENGINE.md §34, build-order step 8 -- DESIGNATION
    // brokers only (DesignationPromotionService itself 404s/no-ops
    // otherwise via its own broker lookup); admin-only, enforced inside the
    // service (not just this permission), same posture as
    // CommissionConfigService/BrokerTierService.override().
    @PostMapping("/api/v1/brokers/{id}/designation-override")
    @RequiresPermission("BROKER_MANAGE")
    public ResponseEntity<Void> setDesignationOverride(@PathVariable UUID id, @Valid @RequestBody DesignationOverrideRequest req) {
        designationPromotionService.manuallyOverride(id, req.designationId(), req.reason());
        return ResponseEntity.noContent().build();
    }

    // §34's own "one decision to surface, not assume" -- built as an
    // explicit clear action (unlike M6's tier-override, which left this as
    // a documented gap); see DesignationPromotionService.clearOverride()'s
    // own javadoc for the reasoning.
    @PostMapping("/api/v1/brokers/{id}/designation-override/clear")
    @RequiresPermission("BROKER_MANAGE")
    public ResponseEntity<Void> clearDesignationOverride(@PathVariable UUID id) {
        designationPromotionService.clearOverride(id);
        return ResponseEntity.noContent().build();
    }
}
