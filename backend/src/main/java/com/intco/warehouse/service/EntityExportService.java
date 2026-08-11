package com.intco.warehouse.service;

import com.baomidou.mybatisplus.annotation.TableField;
import com.intco.warehouse.mapper.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EntityExportService {
    private final WarehouseMapper warehouseMapper;
    private final InventorySnapshotMapper inventorySnapshotMapper;
    private final SkuDailyMetricMapper skuDailyMetricMapper;
    private final WarehouseDailyMetricMapper warehouseDailyMetricMapper;
    private final WarehouseAreaSnapshotMapper warehouseAreaSnapshotMapper;
    private final ExceptionEventMapper exceptionEventMapper;
    private final BomRelationMapper bomRelationMapper;
    private final KpiTargetMapper kpiTargetMapper;
    private final InventoryAgeRuleMapper inventoryAgeRuleMapper;
    private final InventoryAgeBatchMapper inventoryAgeBatchMapper;
    private final InventoryAgeSkuMapper inventoryAgeSkuMapper;

    public EntityExportService(
            WarehouseMapper warehouseMapper,
            InventorySnapshotMapper inventorySnapshotMapper,
            SkuDailyMetricMapper skuDailyMetricMapper,
            WarehouseDailyMetricMapper warehouseDailyMetricMapper,
            WarehouseAreaSnapshotMapper warehouseAreaSnapshotMapper,
            ExceptionEventMapper exceptionEventMapper,
            BomRelationMapper bomRelationMapper,
            KpiTargetMapper kpiTargetMapper,
            InventoryAgeRuleMapper inventoryAgeRuleMapper,
            InventoryAgeBatchMapper inventoryAgeBatchMapper,
            InventoryAgeSkuMapper inventoryAgeSkuMapper) {
        this.warehouseMapper = warehouseMapper;
        this.inventorySnapshotMapper = inventorySnapshotMapper;
        this.skuDailyMetricMapper = skuDailyMetricMapper;
        this.warehouseDailyMetricMapper = warehouseDailyMetricMapper;
        this.warehouseAreaSnapshotMapper = warehouseAreaSnapshotMapper;
        this.exceptionEventMapper = exceptionEventMapper;
        this.bomRelationMapper = bomRelationMapper;
        this.kpiTargetMapper = kpiTargetMapper;
        this.inventoryAgeRuleMapper = inventoryAgeRuleMapper;
        this.inventoryAgeBatchMapper = inventoryAgeBatchMapper;
        this.inventoryAgeSkuMapper = inventoryAgeSkuMapper;
    }

    public List<Map<String, Object>> rows(String query, String firstColumn, int columnCount) {
        if ("AGE_RULE".equals(query)) return toRows(inventoryAgeRuleMapper.selectList(null));
        if ("AGE_BATCH".equals(query)) return toRows(inventoryAgeBatchMapper.selectJoined());
        if ("AGE_SKU".equals(query)) return toRows(inventoryAgeSkuMapper.selectJoined());
        if ("warehouse_id".equals(firstColumn)) return toRows(warehouseMapper.selectList(null));
        if ("warehouse_name".equals(firstColumn)) return toRows(inventorySnapshotMapper.selectJoined(null));
        if ("biz_date".equals(firstColumn)) {
            return columnCount == 33 ? toRows(skuDailyMetricMapper.selectJoined(null)) : toRows(warehouseDailyMetricMapper.selectJoined(null));
        }
        if ("snapshot_date".equals(firstColumn)) return toRows(warehouseAreaSnapshotMapper.selectJoined(null));
        if ("event_id".equals(firstColumn)) return toRows(exceptionEventMapper.selectJoined(null, null));
        if ("project_no".equals(firstColumn)) return toRows(bomRelationMapper.selectList(null));
        if ("kpi_name".equals(firstColumn)) return toRows(kpiTargetMapper.selectList(null));
        throw new IllegalArgumentException("Unsupported export dataset");
    }

    private static List<Map<String, Object>> toRows(List<?> entities) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object entity : entities) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Field field : entity.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    TableField mapping = field.getAnnotation(TableField.class);
                    String columnName = mapping != null && !mapping.value().isEmpty()
                            ? mapping.value()
                            : toSnakeCase(field.getName());
                    row.put(columnName, field.get(entity));
                } catch (IllegalAccessException error) {
                    throw new IllegalStateException("Cannot export entity field " + field.getName(), error);
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private static String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
