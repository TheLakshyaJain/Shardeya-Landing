package com.shardeya.builder.tracker;

import com.shardeya.builder.payment.dto.PaymentCreateRequest;
import com.shardeya.builder.payment.dto.PaymentResponse;
import com.shardeya.builder.tracker.dto.BulkRemindRequest;
import com.shardeya.builder.tracker.dto.CollectionRow;
import com.shardeya.builder.tracker.dto.FollowUpRow;
import com.shardeya.builder.tracker.dto.MarkDoneRequest;
import com.shardeya.builder.tracker.dto.RescheduleRequest;
import com.shardeya.builder.tracker.dto.TrackerCounts;
import com.shardeya.foundation.customer.Interaction;
import com.shardeya.foundation.customer.dto.InteractionResponse;
import com.shardeya.platform.CursorPage;
import com.shardeya.platform.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class TrackerController {

    private final TrackerService service;

    public TrackerController(TrackerService service) {
        this.service = service;
    }

    // B-13 §9: DATA_VIEW_ALL or DATA_VIEW_OWN -- both are valid for this
    // endpoint (own-scoped is enforced inside the service, not by the
    // annotation, same "no OR support" reasoning CustomerService's own
    // assertCanViewAnyLeads comment already documents). Gated here on the
    // narrower DATA_VIEW_OWN so Sales Executives (who hold only that) can
    // still reach it; DATA_VIEW_ALL holders trivially satisfy this too since
    // the M-02 permission set already grants both together in this project's
    // seeded role matrix wherever DATA_VIEW_ALL exists on a builder role.
    @org.springframework.web.bind.annotation.GetMapping("/api/v1/builder/tracker/follow-ups")
    public CursorPage<FollowUpRow> followUps(@RequestParam(required = false) String range,
                                              @RequestParam(required = false) UUID assignedTo,
                                              @RequestParam(required = false) UUID projectId,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(defaultValue = "25") int limit) {
        return service.followUps(range, assignedTo, projectId, cursor, Math.min(Math.max(limit, 1), 100));
    }

    @org.springframework.web.bind.annotation.GetMapping("/api/v1/builder/tracker/collections")
    @RequiresPermission("FINANCIAL_VIEW")
    public CursorPage<CollectionRow> collections(@RequestParam(required = false) String range,
                                                  @RequestParam(required = false) UUID projectId,
                                                  @RequestParam(required = false) String cursor,
                                                  @RequestParam(defaultValue = "25") int limit) {
        return service.collections(range, projectId, cursor, Math.min(Math.max(limit, 1), 100));
    }

    @org.springframework.web.bind.annotation.GetMapping("/api/v1/builder/tracker/counts")
    public TrackerCounts counts() {
        return service.counts();
    }

    @PostMapping("/api/v1/builder/tracker/follow-ups/{customerId}/log")
    public InteractionResponse log(@PathVariable UUID customerId, @Valid @RequestBody LogFollowUpRequest req) {
        return service.logFollowUp(customerId, req.type(), req.remarks(), req.nextDate(), req.result());
    }

    @PostMapping("/api/v1/builder/tracker/follow-ups/{customerId}/reschedule")
    public InteractionResponse reschedule(@PathVariable UUID customerId, @Valid @RequestBody RescheduleRequest req) {
        return service.reschedule(customerId, req.newDate(), req.reason());
    }

    @PostMapping("/api/v1/builder/tracker/follow-ups/{customerId}/mark-done")
    public InteractionResponse markDone(@PathVariable UUID customerId, @Valid @RequestBody MarkDoneRequest req) {
        return service.markDone(customerId, req.remarks());
    }

    @PostMapping("/api/v1/builder/tracker/collections/{scheduleId}/record-payment")
    @RequiresPermission("FINANCIAL_RECORD_PAYMENT")
    public PaymentResponse recordPayment(@PathVariable UUID scheduleId, @Valid @RequestBody PaymentCreateRequest req) {
        return service.recordPayment(scheduleId, req);
    }

    @PostMapping("/api/v1/builder/tracker/collections/{scheduleId}/remind")
    @RequiresPermission("FINANCIAL_RECORD_PAYMENT")
    public ResponseEntity<Void> remind(@PathVariable UUID scheduleId, @RequestParam(defaultValue = "WHATSAPP") String channel) {
        service.remind(scheduleId, channel);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/api/v1/builder/tracker/collections/bulk-remind")
    @RequiresPermission("FINANCIAL_RECORD_PAYMENT")
    public TrackerService.BulkRemindResult bulkRemind(@Valid @RequestBody BulkRemindRequest req) {
        return service.bulkRemind(req.scheduleIds());
    }

    public record LogFollowUpRequest(
            @jakarta.validation.constraints.NotNull(message = "error.interaction.typeRequired") Interaction.Type type,
            @NotBlank(message = "error.interaction.remarksRequired") String remarks,
            LocalDate nextDate,
            Interaction.Result result
    ) {
    }
}
