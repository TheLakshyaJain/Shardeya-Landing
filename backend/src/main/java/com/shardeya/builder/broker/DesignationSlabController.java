package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.DesignationSlabResponse;
import com.shardeya.platform.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DesignationSlabController {

    private final DesignationSlabService service;

    public DesignationSlabController(DesignationSlabService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/designation-slabs")
    @RequiresPermission("BROKER_VIEW")
    public List<DesignationSlabResponse> list() {
        return service.list();
    }
}
