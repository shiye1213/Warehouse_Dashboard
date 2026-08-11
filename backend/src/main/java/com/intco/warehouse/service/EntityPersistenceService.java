package com.intco.warehouse.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.annotation.TableField;
import com.intco.warehouse.entity.*;
import com.intco.warehouse.mapper.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class EntityPersistenceService {
    private final WarehouseMapper warehouseMapper;
    private final WarehouseSkuBaseMapper warehouseSkuBaseMapper;
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
    private final DataImportJobMapper dataImportJobMapper;

    public EntityPersistenceService(
            WarehouseMapper warehouseMapper,
            WarehouseSkuBaseMapper warehouseSkuBaseMapper,
            InventorySnapshotMapper inventorySnapshotMapper,
            SkuDailyMetricMapper skuDailyMetricMapper,
            WarehouseDailyMetricMapper warehouseDailyMetricMapper,
            WarehouseAreaSnapshotMapper warehouseAreaSnapshotMapper,
            ExceptionEventMapper exceptionEventMapper,
            BomRelationMapper bomRelationMapper,
            KpiTargetMapper kpiTargetMapper,
            InventoryAgeRuleMapper inventoryAgeRuleMapper,
            InventoryAgeBatchMapper inventoryAgeBatchMapper,
            InventoryAgeSkuMapper inventoryAgeSkuMapper,
            DataImportJobMapper dataImportJobMapper) {
        this.warehouseMapper = warehouseMapper;
        this.warehouseSkuBaseMapper = warehouseSkuBaseMapper;
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
        this.dataImportJobMapper = dataImportJobMapper;
    }

    public boolean isWarehouseSkuBaseEmpty() {
        return warehouseSkuBaseMapper.selectCount(null) == 0;
    }

    public List<WarehouseEntity> warehouses() {
        return warehouseMapper.selectList(null);
    }

    public WarehouseEntity warehouse(String warehouseId) {
        return warehouseMapper.selectById(warehouseId);
    }

    public void insertWarehouses(List<Object[]> rows) {
        for (Object[] row : rows) {
            WarehouseEntity entity = new WarehouseEntity();
            entity.setWarehouseId((String) row[0]);
            entity.setWarehouseName((String) row[1]);
            entity.setWarehouseType((String) row[2]);
            entity.setWarehouseRole((String) row[3]);
            entity.setAreaCount((Integer) row[4]);
            entity.setCapacityLocations((Integer) row[5]);
            entity.setWarehouseOwner((String) row[6]);
            if (warehouseMapper.selectById(entity.getWarehouseId()) == null) warehouseMapper.insert(entity);
            else warehouseMapper.updateById(entity);
        }
    }
    public void insertWarehouseSkuBases(List<Object[]> rows) { insertRows(rows, WarehouseSkuBaseEntity.class, warehouseSkuBaseMapper); }
    public void insertInventory(List<Object[]> rows) { insertRows(rows, InventorySnapshotEntity.class, inventorySnapshotMapper); }
    public void insertSkuDaily(List<Object[]> rows) { insertRows(rows, SkuDailyMetricEntity.class, skuDailyMetricMapper); }
    public void insertWarehouseDaily(List<Object[]> rows) { insertRows(rows, WarehouseDailyMetricEntity.class, warehouseDailyMetricMapper); }
    public void insertAreaSnapshots(List<Object[]> rows) { insertRows(rows, WarehouseAreaSnapshotEntity.class, warehouseAreaSnapshotMapper); }
    public void insertExceptions(List<Object[]> rows) { insertRows(rows, ExceptionEventEntity.class, exceptionEventMapper); }
    public void insertBom(List<Object[]> rows) { insertRows(rows, BomRelationEntity.class, bomRelationMapper); }
    public void insertTargets(List<Object[]> rows) { insertRows(rows, KpiTargetEntity.class, kpiTargetMapper); }
    public void insertInventoryAgeRules(List<Object[]> rows) { insertRows(rows, InventoryAgeRuleEntity.class, inventoryAgeRuleMapper); }
    public void insertInventoryAgeBatches(List<Object[]> rows) { insertRows(rows, InventoryAgeBatchEntity.class, inventoryAgeBatchMapper); }
    public void insertInventoryAgeSkus(List<Object[]> rows) { insertRows(rows, InventoryAgeSkuEntity.class, inventoryAgeSkuMapper); }

    public void deleteWarehouseDaily(Object bizDate, Object warehouseId) {
        warehouseDailyMetricMapper.delete(Wrappers.lambdaQuery(WarehouseDailyMetricEntity.class)
                .eq(WarehouseDailyMetricEntity::getBizDate, bizDate)
                .eq(WarehouseDailyMetricEntity::getWarehouseId, warehouseId));
    }

    public void clearAll() {
        exceptionEventMapper.delete(null);
        warehouseAreaSnapshotMapper.delete(null);
        warehouseDailyMetricMapper.delete(null);
        skuDailyMetricMapper.delete(null);
        inventorySnapshotMapper.delete(null);
        bomRelationMapper.delete(null);
        kpiTargetMapper.delete(null);
        inventoryAgeBatchMapper.delete(null);
        inventoryAgeSkuMapper.delete(null);
        inventoryAgeRuleMapper.delete(null);
        warehouseSkuBaseMapper.delete(null);
    }

    public void insertImportJob(String id, String fileName, String type, int rows,
                                LocalDateTime startedAt, LocalDateTime finishedAt,
                                String status, String message) {
        DataImportJobEntity job = new DataImportJobEntity();
        job.setImportId(id);
        job.setFileName(fileName);
        job.setImportType(type);
        job.setImportedRows(rows);
        job.setStartedAt(startedAt);
        job.setFinishedAt(finishedAt);
        job.setStatus(status);
        job.setMessage(message);
        dataImportJobMapper.insert(job);
    }

    public LocalDate[] metricDateRange() {
        List<WarehouseDailyMetricEntity> rows = warehouseDailyMetricMapper.selectList(
                Wrappers.lambdaQuery(WarehouseDailyMetricEntity.class)
                        .orderByAsc(WarehouseDailyMetricEntity::getBizDate));
        if (rows.isEmpty()) return new LocalDate[]{null, null};
        return new LocalDate[]{rows.get(0).getBizDate(), rows.get(rows.size() - 1).getBizDate()};
    }

    private static <T> void insertRows(List<Object[]> rows, Class<T> entityType, BaseMapper<T> mapper) {
        List<Field> fields = Arrays.stream(entityType.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> {
                    TableField mapping = field.getAnnotation(TableField.class);
                    return mapping == null || mapping.exist();
                })
                .collect(Collectors.toList());
        for (Object[] values : rows) {
            if (values.length > fields.size()) {
                throw new IllegalArgumentException(entityType.getSimpleName() + " field count mismatch: expected "
                        + fields.size() + " but got " + values.length);
            }
            try {
                T entity = entityType.newInstance();
                for (int index = 0; index < values.length; index++) {
                    Field field = fields.get(index);
                    field.setAccessible(true);
                    field.set(entity, convert(values[index], field.getType()));
                }
                mapper.insert(entity);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot map imported row to " + entityType.getSimpleName(), error);
            }
        }
    }
    private static Object convert(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType == LocalDate.class && value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (targetType == LocalDateTime.class && value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        return value;
    }
}
