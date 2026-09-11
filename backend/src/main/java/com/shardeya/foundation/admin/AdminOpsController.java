package com.shardeya.foundation.admin;

import com.shardeya.foundation.admin.dto.AppErrorLogRow;
import com.shardeya.foundation.admin.dto.MessageDeliveryLogRow;
import com.shardeya.foundation.notification.MessageDelivery;
import com.shardeya.platform.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** M-14 stand-in -- see AdminOpsService's own javadoc. Both endpoints require SETTINGS_MANAGE (Admin/Owner only). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminOpsController {

    private final AdminOpsService service;

    public AdminOpsController(AdminOpsService service) {
        this.service = service;
    }

    @GetMapping("/message-deliveries")
    @RequiresPermission("SETTINGS_MANAGE")
    public List<MessageDeliveryLogRow> messageDeliveries(
            @RequestParam(required = false) MessageDelivery.Status status,
            @RequestParam(defaultValue = "50") int limit) {
        return service.messageDeliveries(status, limit);
    }

    @GetMapping("/errors")
    @RequiresPermission("SETTINGS_MANAGE")
    public List<AppErrorLogRow> errors(@RequestParam(defaultValue = "50") int limit) {
        return service.recentErrors(limit);
    }
}
