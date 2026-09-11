package com.shardeya.foundation.calendar;

import com.shardeya.foundation.calendar.dto.CalendarEventResponse;
import com.shardeya.foundation.calendar.dto.EventCreateRequest;
import com.shardeya.foundation.calendar.dto.EventUpdateRequest;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A single set of endpoints under /api/v1/calendar serves both profiles
 * (M-11's own contract); B-09's "plus" endpoints
 * (assignedTo/projectId/types query params) are the SAME params, not a
 * separate /api/v1/builder/calendar path -- duplicating an identical query
 * shape under a second path would be pure repetition. staff-summary is the
 * one genuinely builder-only addition, so it alone gets the /builder/ prefix.
 */
@RestController
public class CalendarController {

    private final CalendarService service;

    public CalendarController(CalendarService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/calendar")
    public List<CalendarEventResponse> list(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                             @RequestParam(required = false) List<CalendarEvent.EventType> types,
                                             @RequestParam(required = false) UUID assignedTo,
                                             @RequestParam(required = false) UUID projectId) {
        return service.list(from, to, assignedTo, projectId, types);
    }

    @GetMapping("/api/v1/calendar/day/{date}")
    public List<CalendarEventResponse> day(@PathVariable LocalDate date) {
        return service.list(date, date, null, null, null);
    }

    @GetMapping("/api/v1/calendar/counts")
    public Map<LocalDate, Long> counts(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return service.counts(from, to);
    }

    @GetMapping("/api/v1/builder/calendar/staff-summary")
    public Map<UUID, Long> staffSummary(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return service.staffSummary(from, to);
    }

    @PostMapping("/api/v1/calendar/events")
    @RequiresPermission("DATA_CREATE")
    public ResponseEntity<CalendarEventResponse> create(@Valid @RequestBody EventCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createManual(request));
    }

    @PatchMapping("/api/v1/calendar/events/{id}")
    @RequiresPermission("DATA_EDIT_ALL")
    public CalendarEventResponse update(@PathVariable UUID id, @RequestBody EventUpdateRequest request) {
        return service.updateManual(id, request);
    }

    @DeleteMapping("/api/v1/calendar/events/{id}")
    @RequiresPermission("DATA_DELETE")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteManual(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/calendar/events/{id}/complete")
    public CalendarEventResponse complete(@PathVariable UUID id) {
        return service.complete(id);
    }

    @PostMapping("/api/v1/calendar/events/{id}/reschedule")
    @RequiresPermission("DATA_EDIT_ALL")
    public CalendarEventResponse reschedule(@PathVariable UUID id, @RequestBody RescheduleRequest request) {
        return service.reschedule(id, request.newDate(), request.newTime(), request.reason());
    }

    public record RescheduleRequest(LocalDate newDate, LocalTime newTime, String reason) {
    }
}
