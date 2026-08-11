package com.intco.warehouse.controller;

import com.intco.warehouse.service.WarehouseDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "仓库看板", description = "原料库、成品库、箱盒库及库区看板查询接口")
@RestController
@RequestMapping("/api")
public class DashboardController {
    private final WarehouseDataService dataService;

    public DashboardController(WarehouseDataService dataService) {
        this.dataService = dataService;
    }

    @Operation(summary = "查询全部仓库运营总览")
    @GetMapping("/dashboard/overview")
    public Map<String, Object> overview(@RequestParam(defaultValue = "31") int range) {
        return dataService.snapshot(range);
    }

    @Operation(summary = "查询仓库主数据")
    @GetMapping("/warehouses")
    public List<Map<String, Object>> warehouses() {
        return dataService.warehouses();
    }

    @Operation(summary = "查询指定仓库看板数据", description = "返回日趋势、库区、库存、异常、KPI 目标及最新 SKU 作业明细")
    @GetMapping("/dashboard/warehouses/{warehouseId}")
    public ResponseEntity<Map<String, Object>> warehouseDashboard(@PathVariable String warehouseId,
                                                                  @RequestParam(defaultValue = "31") int range) {
        return dataService.warehouseSnapshot(warehouseId, range)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "查询库存库龄与呆滞风险数据", description = "返回最新快照的判定规则、批次明细和 SKU 级呆滞汇总")
    @GetMapping("/dashboard/inventory-aging")
    public Map<String, Object> inventoryAging() {
        return dataService.inventoryAgingSnapshot();
    }

    @Operation(summary = "查询库区详情")
    @GetMapping("/zones/{code}")
    public ResponseEntity<Map<String, Object>> zone(@PathVariable String code) {
        return dataService.zoneDetail(code).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "查询服务健康状态")
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "warehouse-dashboard-api");
        response.put("timestamp", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
        return response;
    }
}
