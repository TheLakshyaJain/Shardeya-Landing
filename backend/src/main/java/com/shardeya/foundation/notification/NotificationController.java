package com.shardeya.foundation.notification;

import com.shardeya.foundation.notification.dto.NotificationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M3 scope: in-app bell only, polling-based (no SSE) -- see
 * NotificationBell's own frontend comment for why polling was chosen over
 * the SSE the M3 kickoff brief originally sketched.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationResponse> list(@RequestParam(defaultValue = "20") int limit) {
        return service.listMine(limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unreadCount());
    }

    // Explicit 204 (not a bare void-returning 200) -- see client.ts's own
    // comment: a void method with no ResponseEntity gets Spring's default
    // 200 OK with an empty body, which the frontend's apiFetch used to
    // choke on (only 204 was treated as "no content"), silently swallowing
    // every call to these two endpoints. Fixed on both sides; this half
    // makes the actual REST semantics correct too, matching the same
    // pattern PlotController.delete() already uses.
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        service.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        service.markAllRead();
        return ResponseEntity.noContent().build();
    }
}
