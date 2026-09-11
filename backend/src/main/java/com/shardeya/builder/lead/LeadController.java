package com.shardeya.builder.lead;

import com.shardeya.foundation.customer.Customer;
import com.shardeya.foundation.customer.CustomerService;
import com.shardeya.foundation.customer.InteractionService;
import com.shardeya.foundation.customer.dto.AssignRequest;
import com.shardeya.foundation.customer.dto.BulkAssignRequest;
import com.shardeya.foundation.customer.dto.CustomerCreateRequest;
import com.shardeya.foundation.customer.dto.CustomerResponse;
import com.shardeya.foundation.customer.dto.CustomerUpdateRequest;
import com.shardeya.foundation.customer.dto.FunnelResponse;
import com.shardeya.foundation.customer.dto.ImportantRequest;
import com.shardeya.foundation.customer.dto.InteractionAmendRequest;
import com.shardeya.foundation.customer.dto.InteractionCreateRequest;
import com.shardeya.foundation.customer.dto.InteractionResponse;
import com.shardeya.foundation.customer.dto.StatusUpdateRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** B-07 Builder Customer / Lead Manager, built on top of M-12's shared CustomerService/InteractionService. */
@RestController
public class LeadController {

    private final CustomerService customerService;
    private final InteractionService interactionService;

    public LeadController(CustomerService customerService, InteractionService interactionService) {
        this.customerService = customerService;
        this.interactionService = interactionService;
    }

    @PostMapping("/api/v1/builder/customers")
    @RequiresPermission("DATA_CREATE")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    @GetMapping("/api/v1/builder/customers")
    public CursorPage<CustomerResponse> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID plotId,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) Customer.Source source,
            @RequestParam(required = false) UUID brokerId,
            @RequestParam(required = false) Customer.Status status,
            @RequestParam(required = false) Boolean important,
            @RequestParam(required = false) String followUp,
            @RequestParam(required = false) String search) {
        return customerService.list(cursor, limit, projectId, plotId, assignedTo, source, brokerId, status, important, followUp, search);
    }

    @GetMapping("/api/v1/builder/customers/unassigned")
    public List<CustomerResponse> unassigned(@RequestParam(defaultValue = "50") int limit) {
        return customerService.unassigned(limit);
    }

    @GetMapping("/api/v1/builder/customers/funnel")
    public FunnelResponse funnel(@RequestParam(required = false) UUID projectId) {
        return customerService.funnel(projectId);
    }

    @GetMapping("/api/v1/customers/follow-ups")
    public List<CustomerResponse> followUps(@RequestParam String range) {
        return customerService.followUps(range);
    }

    @GetMapping("/api/v1/customers/{id}")
    public CustomerResponse get(@PathVariable UUID id) {
        return customerService.get(id);
    }

    @PatchMapping("/api/v1/customers/{id}")
    public CustomerResponse update(@PathVariable UUID id, @RequestBody CustomerUpdateRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/api/v1/customers/{id}")
    @RequiresPermission("DATA_DELETE")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/customers/{id}/restore")
    @RequiresPermission("DATA_DELETE")
    public ResponseEntity<Void> restore(@PathVariable UUID id) {
        customerService.restore(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/customers/{id}/important")
    public CustomerResponse important(@PathVariable UUID id, @RequestBody ImportantRequest request) {
        return customerService.setImportant(id, request.important());
    }

    @PatchMapping("/api/v1/customers/{id}/status")
    public CustomerResponse status(@PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest request) {
        return customerService.updateStatus(id, request.status(), request.note());
    }

    @PatchMapping("/api/v1/customers/{id}/assign")
    @RequiresPermission("LEAD_ASSIGN")
    public CustomerResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
        return customerService.assign(id, request.userId());
    }

    @PostMapping("/api/v1/customers/bulk-assign")
    @RequiresPermission("LEAD_ASSIGN")
    public ResponseEntity<Void> bulkAssign(@Valid @RequestBody BulkAssignRequest request) {
        customerService.bulkAssign(request.customerIds(), request.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/customers/{id}/interactions")
    public List<InteractionResponse> interactions(@PathVariable UUID id, @RequestParam(defaultValue = "50") int limit) {
        return interactionService.list(id, limit);
    }

    @PostMapping("/api/v1/customers/{id}/interactions")
    @RequiresPermission("DATA_CREATE")
    public ResponseEntity<InteractionResponse> addInteraction(@PathVariable UUID id, @Valid @RequestBody InteractionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(interactionService.create(id, request));
    }

    @PatchMapping("/api/v1/interactions/{id}")
    public InteractionResponse amend(@PathVariable UUID id, @Valid @RequestBody InteractionAmendRequest request) {
        return interactionService.amend(id, request);
    }
}
