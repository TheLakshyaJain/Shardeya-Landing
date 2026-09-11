package com.shardeya.builder.payment;

import com.shardeya.builder.payment.dto.ScheduleCreateRequest;
import com.shardeya.builder.payment.dto.ScheduleResponse;
import com.shardeya.builder.payment.dto.ScheduleUpdateRequest;
import com.shardeya.builder.payment.dto.WaiveRequest;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ScheduleController {

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    // M4: previously ungated -- see PlotSaleController's identical note.
    @GetMapping("/api/v1/sales/{id}/schedule")
    @RequiresPermission("FINANCIAL_VIEW")
    public List<ScheduleResponse> list(@PathVariable UUID id) {
        return service.list(id);
    }

    @PostMapping("/api/v1/sales/{id}/schedule")
    @RequiresPermission("FINANCIAL_EDIT")
    public ResponseEntity<ScheduleResponse> add(@PathVariable UUID id, @Valid @RequestBody ScheduleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(id, request));
    }

    @PatchMapping("/api/v1/schedule/{id}")
    @RequiresPermission("FINANCIAL_EDIT")
    public ScheduleResponse update(@PathVariable UUID id, @RequestBody ScheduleUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/api/v1/schedule/{id}")
    @RequiresPermission("FINANCIAL_EDIT")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/v1/schedule/{id}/waive")
    @RequiresPermission("FINANCIAL_EDIT")
    public ScheduleResponse waive(@PathVariable UUID id, @Valid @RequestBody WaiveRequest request) {
        return service.waive(id, request);
    }

    @PatchMapping("/api/v1/schedule/{id}/reminder")
    @RequiresPermission("FINANCIAL_EDIT")
    public ScheduleResponse setReminder(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        return service.setReminderEnabled(id, Boolean.TRUE.equals(body.get("enabled")));
    }
}
