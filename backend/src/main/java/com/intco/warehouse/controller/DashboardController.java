package com.intco.warehouse.controller;

import com.intco.warehouse.service.WarehouseDataService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final WarehouseDataService dataService;

    public DashboardController(WarehouseDataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping("/dashboard/overview")
    public Map<String, Object> overview(@RequestParam(defaultValue = "31") int range) {
        return dataService.snapshot(range);
    }

    @GetMapping("/zones/{code}")
    public ResponseEntity<Map<String, Object>> zone(@PathVariable String code) {
        return dataService.zoneDetail(code).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "warehouse-dashboard-api");
        response.put("timestamp", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
        return response;
    }
}
