package com.shardeya.builder.dashboard;

import com.shardeya.builder.dashboard.dto.DashboardResponse;
import com.shardeya.foundation.notification.dto.NotificationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** B-01 -- no permission gate beyond authentication itself; every card/section internally checks its own permission and omits itself rather than 403ing the whole page (§9). */
@RestController
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/builder/dashboard")
    public DashboardResponse dashboard() {
        return service.dashboard();
    }

    @GetMapping("/api/v1/builder/dashboard/activity")
    public List<NotificationResponse> activity(@RequestParam(defaultValue = "20") int limit) {
        return service.activity(limit);
    }
}
