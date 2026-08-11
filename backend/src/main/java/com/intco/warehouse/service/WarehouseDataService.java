package com.intco.warehouse.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.intco.warehouse.entity.*;
import com.intco.warehouse.mapper.*;
import com.intco.warehouse.model.DashboardData;
import com.intco.warehouse.model.DashboardData.Alert;
import com.intco.warehouse.model.DashboardData.DailyMetric;
import com.intco.warehouse.model.DashboardData.Target;
import com.intco.warehouse.model.DashboardData.WarehouseDailyMetric;
import com.intco.warehouse.model.DashboardData.Zone;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WarehouseDataService {
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
    private final DataImportJobMapper dataImportJobMapper;
    private final ConcurrentQueryExecutor queryExecutor;

    public WarehouseDataService(
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
            InventoryAgeSkuMapper inventoryAgeSkuMapper,
            DataImportJobMapper dataImportJobMapper,
            ConcurrentQueryExecutor queryExecutor) {
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
        this.dataImportJobMapper = dataImportJobMapper;
        this.queryExecutor = queryExecutor;
    }

    public DashboardData currentData() {
        CompletableFuture<List<WarehouseDailyMetric>> dailyQuery = queryExecutor.submit(() -> loadWarehouseDaily(null));
        CompletableFuture<List<Zone>> zoneQuery = queryExecutor.submit(() -> loadLatestZones(null));
        CompletableFuture<List<Alert>> alertQuery = queryExecutor.submit(() -> loadAlerts(null));
        CompletableFuture<List<Target>> targetQuery = queryExecutor.submit(this::loadTargets);
        CompletableFuture<Long> warehouseCountQuery = queryExecutor.submit(() -> warehouseMapper.selectCount(null));
        queryExecutor.awaitAll(dailyQuery, zoneQuery, alertQuery, targetQuery, warehouseCountQuery);

        DashboardData data = new DashboardData();
        data.setWarehouseDaily(queryExecutor.await(dailyQuery));
        data.setDaily(aggregateDaily(data.getWarehouseDaily()));
        data.setZones(queryExecutor.await(zoneQuery));
        data.setAlerts(queryExecutor.await(alertQuery));
        data.setTargets(queryExecutor.await(targetQuery));
        data.setMeta(buildMeta(data.getWarehouseDaily(), data.getZones(), data.getAlerts(),
                queryExecutor.await(warehouseCountQuery)));
        return data;
    }

    public Map<String, Object> snapshot(int requestedRange) {
        DashboardData data = currentData();
        int range = Math.max(1, Math.min(requestedRange, 366));
        List<DailyMetric> allRows = data.getDaily();
        List<DailyMetric> trend = new ArrayList<>(allRows.subList(Math.max(0, allRows.size() - range), allRows.size()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meta", data.getMeta());
        result.put("summary", buildSummary(allRows, data.getZones(), data.getAlerts()));
        result.put("trend", trend);
        result.put("warehouseDaily", data.getWarehouseDaily());
        result.put("zones", data.getZones());
        result.put("alerts", data.getAlerts());
        result.put("targets", data.getTargets());
        result.put("forklifts", data.getForklifts());
        return result;
    }

    public List<Map<String, Object>> warehouses() {
        return warehouseMapper.selectList(Wrappers.lambdaQuery(WarehouseEntity.class).orderByAsc(WarehouseEntity::getWarehouseId)).stream()
                .map(row -> mapOf(
                        "warehouseId", row.getWarehouseId(), "warehouseName", row.getWarehouseName(),
                        "warehouseType", row.getWarehouseType(), "warehouseRole", row.getWarehouseRole(),
                        "areaCount", row.getAreaCount(), "capacityLocations", row.getCapacityLocations(),
                        "warehouseOwner", row.getWarehouseOwner()))
                .collect(Collectors.toList());
    }

    public Map<String, Object> inventoryAgingSnapshot() {
        CompletableFuture<List<InventoryAgeRuleEntity>> ruleQuery = queryExecutor.submit(() ->
                inventoryAgeRuleMapper.selectList(Wrappers.lambdaQuery(InventoryAgeRuleEntity.class)
                        .orderByAsc(InventoryAgeRuleEntity::getRuleType, InventoryAgeRuleEntity::getRuleName)));
        CompletableFuture<List<InventoryAgeBatchEntity>> batchQuery = queryExecutor.submit(() ->
                inventoryAgeBatchMapper.selectList(Wrappers.lambdaQuery(InventoryAgeBatchEntity.class)
                        .orderByDesc(InventoryAgeBatchEntity::getSnapshotDate)
                        .orderByAsc(InventoryAgeBatchEntity::getWarehouseId, InventoryAgeBatchEntity::getAgeBatchId)));
        CompletableFuture<List<InventoryAgeSkuEntity>> skuQuery = queryExecutor.submit(() ->
                inventoryAgeSkuMapper.selectList(Wrappers.lambdaQuery(InventoryAgeSkuEntity.class)
                        .orderByDesc(InventoryAgeSkuEntity::getSnapshotDate)
                        .orderByAsc(InventoryAgeSkuEntity::getWarehouseId, InventoryAgeSkuEntity::getProjectNo,
                                InventoryAgeSkuEntity::getMaterialCode)));
        queryExecutor.awaitAll(ruleQuery, batchQuery, skuQuery);

        List<InventoryAgeBatchEntity> batchHistory = queryExecutor.await(batchQuery);
        List<InventoryAgeSkuEntity> skuHistory = queryExecutor.await(skuQuery);
        LocalDate latestBatchDate = batchHistory.stream().map(InventoryAgeBatchEntity::getSnapshotDate)
                .max(LocalDate::compareTo).orElse(null);
        LocalDate latestSkuDate = skuHistory.stream().map(InventoryAgeSkuEntity::getSnapshotDate)
                .max(LocalDate::compareTo).orElse(null);

        List<Map<String, Object>> batches = batchHistory.stream()
                .filter(row -> latestBatchDate == null || latestBatchDate.equals(row.getSnapshotDate()))
                .map(row -> mapOf(
                        "snapshotDate", row.getSnapshotDate(), "batchId", row.getAgeBatchId(),
                        "warehouseId", row.getWarehouseId(), "warehouseName", row.getWarehouseName(),
                        "warehouseType", row.getWarehouseType(), "projectNo", row.getProjectNo(),
                        "projectName", row.getProjectName(), "materialCode", row.getMaterialCode(),
                        "materialName", row.getMaterialName(), "sku", row.getProjectMaterialSku(),
                        "materialCategory", row.getMaterialCategory(), "model", row.getModel(), "uom", row.getUom(),
                        "batchNo", row.getBatchNo(), "receiptDate", row.getReceiptDate(), "ageDays", row.getAgeDays(),
                        "ageBucket", row.getAgeBucket(), "onHandQty", number(row.getBatchOnHandQty()),
                        "availableQty", number(row.getAvailableQty()), "inventoryAmount", number(row.getInventoryAmount()),
                        "lastOutboundDate", row.getLastOutboundDate(), "daysSinceLastOutbound", row.getDaysSinceLastOutbound(),
                        "outboundRate30d", number(row.getOutboundRate30d()), "movementStatus", row.getMovementStatus(),
                        "stagnantLevel", row.getStagnantLevel(), "isStagnant", Boolean.TRUE.equals(row.getIsStagnant()),
                        "stagnantScore", number(row.getStagnantScore()), "priority", row.getPriority(),
                        "recommendedAction", row.getRecommendedAction(), "owner", row.getOwner()))
                .collect(Collectors.toList());

        List<Map<String, Object>> skus = skuHistory.stream()
                .filter(row -> latestSkuDate == null || latestSkuDate.equals(row.getSnapshotDate()))
                .sorted(Comparator.comparing((InventoryAgeSkuEntity row) -> Boolean.TRUE.equals(row.getIsStagnant())).reversed()
                        .thenComparing(InventoryAgeSkuEntity::getMaxAgeDays, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(row -> mapOf(
                        "snapshotDate", row.getSnapshotDate(), "warehouseId", row.getWarehouseId(),
                        "warehouseName", row.getWarehouseName(), "warehouseType", row.getWarehouseType(),
                        "projectNo", row.getProjectNo(), "projectName", row.getProjectName(),
                        "materialCode", row.getMaterialCode(), "materialName", row.getMaterialName(),
                        "sku", row.getProjectMaterialSku(), "materialCategory", row.getMaterialCategory(),
                        "color", row.getColor(), "model", row.getModel(), "uom", row.getUom(),
                        "batchCount", row.getBatchCount(), "onHandQty", number(row.getOnHandQty()),
                        "availableQty", number(row.getAvailableQty()), "inventoryAmount", number(row.getInventoryAmount()),
                        "weightedAvgAgeDays", number(row.getWeightedAvgAgeDays()), "maxAgeDays", row.getMaxAgeDays(),
                        "dominantAgeBucket", row.getDominantAgeBucket(), "outboundQty30d", number(row.getOutboundQty30d()),
                        "outboundRate30d", number(row.getOutboundRate30d()), "latestOutboundDate", row.getLatestSkuOutboundDate(),
                        "daysSinceLastOutbound", row.getDaysSinceLastSkuOutbound(), "stagnantBatchCount", row.getStagnantBatchCount(),
                        "stagnantInventoryAmount", number(row.getStagnantInventoryAmount()),
                        "stagnationRatio", number(row.getStagnationRatio()), "stagnantLevel", row.getStagnantLevel(),
                        "isStagnant", Boolean.TRUE.equals(row.getIsStagnant()), "stagnantScore", number(row.getStagnantScore()),
                        "priority", row.getPriority(), "recommendedAction", row.getRecommendedAction(), "owner", row.getOwner()))
                .collect(Collectors.toList());

        List<Map<String, Object>> rules = queryExecutor.await(ruleQuery).stream()
                .map(row -> mapOf("type", row.getRuleType(), "name", row.getRuleName(),
                        "condition", row.getRuleCondition(), "level", row.getResultLevel(),
                        "action", row.getActionGuidance(), "scope", row.getApplicableScope()))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meta", mapOf("snapshotDate", latestBatchDate != null ? latestBatchDate : latestSkuDate,
                "source", "MySQL · inventory_age_batch / inventory_age_sku",
                "batchCount", batches.size(), "skuCount", skus.size(), "stagnantThresholdDays", 180,
                "severeThresholdDays", 365, "noMovementThresholdDays", 90));
        result.put("rules", rules);
        result.put("batches", batches);
        result.put("skus", skus);
        return result;
    }

    public Optional<Map<String, Object>> warehouseSnapshot(String warehouseId, int requestedRange) {
        WarehouseEntity warehouseRow = warehouseMapper.selectById(warehouseId);
        if (warehouseRow == null) return Optional.empty();
        Map<String, Object> warehouse = mapOf(
                "warehouseId", warehouseRow.getWarehouseId(), "warehouseName", warehouseRow.getWarehouseName(),
                "warehouseType", warehouseRow.getWarehouseType(), "warehouseRole", warehouseRow.getWarehouseRole(),
                "areaCount", warehouseRow.getAreaCount(), "capacityLocations", warehouseRow.getCapacityLocations(),
                "owners", splitOwners(warehouseRow.getWarehouseOwner()));

        CompletableFuture<List<WarehouseDailyMetric>> dailyQuery =
                queryExecutor.submit(() -> loadWarehouseDaily(warehouseId));
        CompletableFuture<List<Zone>> zoneQuery = queryExecutor.submit(() -> loadLatestZones(warehouseId));
        CompletableFuture<List<InventorySnapshotEntity>> inventoryQuery =
                queryExecutor.submit(() -> loadInventoryRows(warehouseId));
        CompletableFuture<List<Target>> targetQuery = queryExecutor.submit(this::loadTargets);
        CompletableFuture<List<Map<String, Object>>> alertQuery =
                queryExecutor.submit(() -> loadDetailedAlerts(warehouseId));
        CompletableFuture<List<Map<String, Object>>> skuQuery =
                queryExecutor.submit(() -> loadLatestSkuOperations(warehouseId));
        queryExecutor.awaitAll(dailyQuery, zoneQuery, inventoryQuery, targetQuery, alertQuery, skuQuery);

        List<WarehouseDailyMetric> allDaily = queryExecutor.await(dailyQuery);
        List<Zone> zones = queryExecutor.await(zoneQuery);
        List<InventorySnapshotEntity> inventoryRows = queryExecutor.await(inventoryQuery);
        List<Map<String, Object>> inventory = mapInventory(inventoryRows, false);
        List<Map<String, Object>> stocks = mapInventory(inventoryRows, true);
        List<Target> targets = queryExecutor.await(targetQuery);
        List<Map<String, Object>> alertMaps = queryExecutor.await(alertQuery);
        List<Map<String, Object>> skuOperations = queryExecutor.await(skuQuery);
        int range = Math.max(1, Math.min(requestedRange, 366));
        List<WarehouseDailyMetric> daily =
                new ArrayList<>(allDaily.subList(Math.max(0, allDaily.size() - range), allDaily.size()));

        LocalDate start = allDaily.isEmpty() ? null : LocalDate.parse(allDaily.get(0).getDate());
        LocalDate end = allDaily.isEmpty() ? null : LocalDate.parse(allDaily.get(allDaily.size() - 1).getDate());
        warehouse.put("period", start == null ? "" : start + " 至 " + end);
        warehouse.put("latestDate", end == null ? null : end.toString());
        warehouse.put("snapshotDate", zones.isEmpty() ? null : zones.get(0).getSnapshotDate());
        warehouse.put("source", "MySQL · warehouse_dashboard");

        List<Map<String, Object>> dailyMaps = daily.stream().map(this::dailyMap).collect(Collectors.toList());

        List<Map<String, Object>> openAlerts = alertMaps.stream()
                .filter(row -> !"已关闭".equals(row.get("status"))).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meta", warehouse);
        result.put("summary", buildWarehouseSummary(allDaily, zones, alertMaps, stocks));
        result.put("daily", dailyMaps);
        result.put("trend", dailyMaps);
        result.put("zones", zones.stream().map(this::zoneMap).collect(Collectors.toList()));
        result.put("alerts", alertMaps);
        result.put("openExceptions", openAlerts);
        result.put("inventory", inventory);
        result.put("stocks", stocks);
        result.put("skuOperations", skuOperations);
        result.put("targets", targets.stream().map(this::targetMap).collect(Collectors.toList()));
        result.put("exceptionBreakdown", exceptionBreakdown(alertMaps));
        return Optional.of(result);
    }

    public Optional<Map<String, Object>> zoneDetail(String code) {
        CompletableFuture<List<Zone>> zoneQuery = queryExecutor.submit(() ->
                warehouseAreaSnapshotMapper.selectList(Wrappers.lambdaQuery(WarehouseAreaSnapshotEntity.class)
                                .eq(WarehouseAreaSnapshotEntity::getAreaId, code)
                                .orderByDesc(WarehouseAreaSnapshotEntity::getSnapshotDate).last("LIMIT 1"))
                        .stream().map(this::mapZone).collect(Collectors.toList()));
        CompletableFuture<List<Alert>> alertQuery = queryExecutor.submit(() -> loadAlertsByArea(code));
        queryExecutor.awaitAll(zoneQuery, alertQuery);

        List<Zone> matches = queryExecutor.await(zoneQuery);
        if (matches.isEmpty()) return Optional.empty();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("zone", matches.get(0));
        result.put("relatedAlerts", queryExecutor.await(alertQuery));
        return Optional.of(result);
    }

    public Map<String, Object> dataStatus() {
        CompletableFuture<Long> warehouses = queryExecutor.submit(() -> warehouseMapper.selectCount(null));
        CompletableFuture<Long> inventorySnapshots = queryExecutor.submit(() -> inventorySnapshotMapper.selectCount(null));
        CompletableFuture<Long> skuDailyMetrics = queryExecutor.submit(() -> skuDailyMetricMapper.selectCount(null));
        CompletableFuture<Long> warehouseDailyMetrics = queryExecutor.submit(() -> warehouseDailyMetricMapper.selectCount(null));
        CompletableFuture<Long> areaSnapshots = queryExecutor.submit(() -> warehouseAreaSnapshotMapper.selectCount(null));
        CompletableFuture<Long> exceptionEvents = queryExecutor.submit(() -> exceptionEventMapper.selectCount(null));
        CompletableFuture<Long> bomRelations = queryExecutor.submit(() -> bomRelationMapper.selectCount(null));
        CompletableFuture<Long> kpiTargets = queryExecutor.submit(() -> kpiTargetMapper.selectCount(null));
        CompletableFuture<Long> inventoryAgeRules = queryExecutor.submit(() -> inventoryAgeRuleMapper.selectCount(null));
        CompletableFuture<Long> inventoryAgeBatches = queryExecutor.submit(() -> inventoryAgeBatchMapper.selectCount(null));
        CompletableFuture<Long> inventoryAgeSkus = queryExecutor.submit(() -> inventoryAgeSkuMapper.selectCount(null));
        CompletableFuture<Long> importJobs = queryExecutor.submit(() -> dataImportJobMapper.selectCount(null));
        CompletableFuture<List<WarehouseDailyMetric>> dailyQuery = queryExecutor.submit(() -> loadWarehouseDaily(null));
        CompletableFuture<List<Zone>> zoneQuery = queryExecutor.submit(() -> loadLatestZones(null));
        CompletableFuture<List<Alert>> alertQuery = queryExecutor.submit(() -> loadAlerts(null));
        queryExecutor.awaitAll(warehouses, inventorySnapshots, skuDailyMetrics, warehouseDailyMetrics,
                areaSnapshots, exceptionEvents, bomRelations, kpiTargets, inventoryAgeRules,
                inventoryAgeBatches, inventoryAgeSkus, importJobs, dailyQuery, zoneQuery, alertQuery);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("warehouses", queryExecutor.await(warehouses));
        result.put("inventorySnapshots", queryExecutor.await(inventorySnapshots));
        result.put("skuDailyMetrics", queryExecutor.await(skuDailyMetrics));
        result.put("warehouseDailyMetrics", queryExecutor.await(warehouseDailyMetrics));
        result.put("areaSnapshots", queryExecutor.await(areaSnapshots));
        result.put("exceptionEvents", queryExecutor.await(exceptionEvents));
        result.put("bomRelations", queryExecutor.await(bomRelations));
        result.put("kpiTargets", queryExecutor.await(kpiTargets));
        result.put("inventoryAgeRules", queryExecutor.await(inventoryAgeRules));
        result.put("inventoryAgeBatches", queryExecutor.await(inventoryAgeBatches));
        result.put("inventoryAgeSkus", queryExecutor.await(inventoryAgeSkus));
        result.put("importJobs", queryExecutor.await(importJobs));
        result.putAll(buildMeta(queryExecutor.await(dailyQuery), queryExecutor.await(zoneQuery),
                queryExecutor.await(alertQuery), queryExecutor.await(warehouses)));
        return result;
    }

    private List<Map<String, Object>> loadLatestSkuOperations(String warehouseId) {
        List<SkuDailyMetricEntity> rows = skuDailyMetricMapper.selectList(
                Wrappers.lambdaQuery(SkuDailyMetricEntity.class)
                        .eq(SkuDailyMetricEntity::getWarehouseId, warehouseId)
                        .orderByDesc(SkuDailyMetricEntity::getBizDate)
                        .orderByAsc(SkuDailyMetricEntity::getProjectNo, SkuDailyMetricEntity::getMaterialCode));
        if (rows.isEmpty()) return new ArrayList<>();
        LocalDate latestDate = rows.get(0).getBizDate();
        return rows.stream()
                .filter(row -> latestDate.equals(row.getBizDate()))
                .map(row -> mapOf(
                        "date", row.getBizDate().toString(),
                        "projectNo", row.getProjectNo(),
                        "projectName", row.getProjectName(),
                        "materialCode", row.getMaterialCode(),
                        "materialName", row.getMaterialName(),
                        "areaCode", row.getAreaId(),
                        "areaName", row.getAreaName(),
                        "uom", row.getUom(),
                        "inboundOrders", row.getInboundOrderCount(),
                        "inboundLines", row.getInboundLineCount(),
                        "inboundQty", number(row.getInboundQty()),
                        "outboundOrders", row.getOutboundOrderCount(),
                        "outboundLines", row.getOutboundLineCount(),
                        "outboundQty", number(row.getOutboundQty()),
                        "pickingTasks", row.getPickingTaskCount(),
                        "forkliftTasks", row.getForkliftTaskCount(),
                        "receiptTimely", number(row.getReceiptTimelyRate()),
                        "deliveryTimely", number(row.getDeliveryTimelyRate()),
                        "exceptions", row.getExceptionCount()))
                .collect(Collectors.toList());
    }
    private List<WarehouseDailyMetric> loadWarehouseDaily(String warehouseId) {
        return warehouseDailyMetricMapper.selectList(Wrappers.lambdaQuery(WarehouseDailyMetricEntity.class)
                        .eq(warehouseId != null, WarehouseDailyMetricEntity::getWarehouseId, warehouseId).orderByAsc(WarehouseDailyMetricEntity::getBizDate, WarehouseDailyMetricEntity::getWarehouseId)).stream()
                .map(this::mapWarehouseDaily).collect(Collectors.toList());

    }
    private WarehouseDailyMetric mapWarehouseDaily(WarehouseDailyMetricEntity source) {
        WarehouseDailyMetric row = new WarehouseDailyMetric();
        row.setDate(source.getBizDate().toString());
        row.setWarehouseId(source.getWarehouseId());
        row.setWarehouseName(source.getWarehouseName());
        row.setWarehouseType(source.getWarehouseType());
        row.setInboundOrders(source.getInboundOrderCount());
        row.setOutboundOrders(source.getOutboundOrderCount());
        row.setRawInboundTon(number(source.getRawInboundTon()));
        row.setRawOutboundTon(number(source.getRawOutboundTon()));
        row.setFinishedInboundCarton(source.getFinishedInboundCarton());
        row.setFinishedOutboundCarton(source.getFinishedOutboundCarton());
        row.setPackagingInboundPiece(source.getPackagingInboundPiece());
        row.setPackagingOutboundPiece(source.getPackagingOutboundPiece());
        row.setInbound(source.getFinishedInboundCarton());
        row.setOutbound(source.getFinishedOutboundCarton());
        row.setPicking(source.getPickingTaskCount());
        row.setForkliftTasks(source.getForkliftTaskCount());
        row.setInventoryAccuracy(number(source.getInventoryAccuracy()));
        row.setReceivingTimely(number(source.getReceiptTimelyRate()));
        row.setDeliveryTimely(number(source.getDeliveryTimelyRate()));
        row.setExceptions(source.getExceptionCount());
        row.setReceiptMinutes(number(source.getAvgReceiptMinutes()));
        row.setPickingMinutes(number(source.getAvgPickingMinutes()));
        row.setAverageDuration((row.getReceiptMinutes() + row.getPickingMinutes()) / 2d);
        row.setDockUtilization(number(source.getDockUtilizationRate()));
        row.setOvertimeHours(number(source.getOvertimeHours()));
        return row;
    }

    private List<DailyMetric> aggregateDaily(List<WarehouseDailyMetric> rows) {
        Map<String, List<WarehouseDailyMetric>> byDate = rows.stream().collect(Collectors.groupingBy(WarehouseDailyMetric::getDate, LinkedHashMap::new, Collectors.toList()));
        List<DailyMetric> result = new ArrayList<>();
        byDate.forEach((date, dayRows) -> {
            DailyMetric row = new DailyMetric();
            row.setDate(date);
            row.setInbound(dayRows.stream().mapToInt(WarehouseDailyMetric::getFinishedInboundCarton).sum());
            row.setOutbound(dayRows.stream().mapToInt(WarehouseDailyMetric::getFinishedOutboundCarton).sum());
            row.setPicking(sumInt(dayRows, DailyMetric::getPicking));
            row.setForkliftTasks(sumInt(dayRows, DailyMetric::getForkliftTasks));
            row.setInventoryAccuracy(average(dayRows, DailyMetric::getInventoryAccuracy));
            row.setReceivingTimely(average(dayRows, DailyMetric::getReceivingTimely));
            row.setDeliveryTimely(average(dayRows, DailyMetric::getDeliveryTimely));
            row.setExceptions(sumInt(dayRows, DailyMetric::getExceptions));
            row.setReceiptMinutes(average(dayRows, DailyMetric::getReceiptMinutes));
            row.setPickingMinutes(average(dayRows, DailyMetric::getPickingMinutes));
            row.setAverageDuration((row.getReceiptMinutes() + row.getPickingMinutes()) / 2d);
            row.setDockUtilization(average(dayRows, DailyMetric::getDockUtilization));
            row.setOvertimeHours(dayRows.stream().mapToDouble(DailyMetric::getOvertimeHours).sum());
            result.add(row);
        });
        result.sort(Comparator.comparing(DailyMetric::getDate));
        return result;
    }

    private List<Zone> loadLatestZones(String warehouseId) {
        List<WarehouseAreaSnapshotEntity> snapshots = warehouseAreaSnapshotMapper.selectList(Wrappers.lambdaQuery(WarehouseAreaSnapshotEntity.class)
                .eq(warehouseId != null, WarehouseAreaSnapshotEntity::getWarehouseId, warehouseId)
                .orderByAsc(WarehouseAreaSnapshotEntity::getWarehouseId, WarehouseAreaSnapshotEntity::getAreaId));
        Map<String, LocalDate> latest = new LinkedHashMap<>();
        for (WarehouseAreaSnapshotEntity snapshot : snapshots) latest.merge(snapshot.getWarehouseId(), snapshot.getSnapshotDate(), (a, b) -> a.isAfter(b) ? a : b);
        return snapshots.stream().filter(row -> row.getSnapshotDate().equals(latest.get(row.getWarehouseId())))
                .map(this::mapZone).collect(Collectors.toList());
    }

    private Zone mapZone(WarehouseAreaSnapshotEntity source) {
        Zone zone = new Zone();
        zone.setSnapshotDate(source.getSnapshotDate().toString());
        zone.setWarehouse(source.getWarehouseName());
        zone.setCode(source.getAreaId());
        zone.setName(source.getAreaName());
        zone.setCapacity(source.getCapacityLocations());
        zone.setOccupied(source.getOccupiedLocations());
        zone.setAvailable(source.getAvailableLocations());
        zone.setOccupancy(number(source.getOccupancyRate()));
        zone.setMaterialTypes(source.getMaterialTypeCount());
        zone.setAbnormal(source.getAbnormalLocationCount());
        zone.setFrozen((int) Math.round(number(source.getFrozenQty())));
        zone.setManager(source.getAreaOwner());
        zone.setStatus(source.getStatus());
        return zone;
    }

    private List<Alert> loadAlerts(String warehouseId) {
        return exceptionEventMapper.selectList(Wrappers.lambdaQuery(ExceptionEventEntity.class)
                        .eq(warehouseId != null, ExceptionEventEntity::getWarehouseId, warehouseId).orderByDesc(ExceptionEventEntity::getEventTime)).stream()
                .map(this::mapAlert).collect(Collectors.toList());
    }

    private List<Alert> loadAlertsByArea(String areaId) {
        return exceptionEventMapper.selectList(Wrappers.lambdaQuery(ExceptionEventEntity.class)
                        .eq(ExceptionEventEntity::getAreaId, areaId).orderByDesc(ExceptionEventEntity::getEventTime)).stream()
                .map(this::mapAlert).collect(Collectors.toList());
    }

    private Alert mapAlert(ExceptionEventEntity source) {
        java.time.LocalDateTime time = source.getEventTime();
        Alert alert = new Alert();
        alert.setId(source.getEventId());
        alert.setDate(time.toLocalDate().toString());
        alert.setTime(time.toLocalTime().toString());
        alert.setType(source.getEventType());
        alert.setWarehouse(source.getWarehouseName());
        alert.setZoneCode(source.getAreaId());
        alert.setZone(source.getAreaName());
        alert.setTitle(alert.getType() + " · " + alert.getZone());
        alert.setSeverity(source.getSeverity());
        alert.setStatus(source.getHandlingStatus());
        alert.setOwner(source.getOwner());
        alert.setResponseMinutes(source.getResponseMinutes() == null ? 0 : source.getResponseMinutes());
        alert.setSlaHours(number(source.getSlaHours()));
        int duration = source.getDurationMinutes() == null ? 0 : source.getDurationMinutes();
        alert.setDurationHours(duration / 60d);
        alert.setSlaBreached(Boolean.TRUE.equals(source.getIsSlaBreached()));
        java.time.LocalDateTime closed = source.getCloseTime();
        alert.setClosedAt(closed == null ? null : closed.toString());
        alert.setDescription("根因：" + source.getRootCause());
        alert.setRecommendation(source.getActionTaken());
        alert.setMaterial(source.getMaterialName());
        alert.setProject(source.getProjectName());
        return alert;
    }

    private List<Target> loadTargets() {
        return kpiTargetMapper.selectList(Wrappers.lambdaQuery(KpiTargetEntity.class).orderByAsc(KpiTargetEntity::getKpiName)).stream()
                .map(source -> {
            Target target = new Target();
            target.setKey(targetKey(source.getKpiName()));
            target.setName(source.getKpiName());
            target.setTarget(number(source.getTargetValue()));
            target.setUnit(source.getUnit());
            target.setRule(source.getWarningRule());
            target.setDefinition(source.getCalculationDefinition());
            target.setSource(source.getDataSource());
            return target;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildMeta(List<WarehouseDailyMetric> rows, List<Zone> zones,
                                          List<Alert> alerts, long warehouseCount) {
        LocalDate start = rows.stream().map(WarehouseDailyMetric::getDate).map(LocalDate::parse)
                .min(LocalDate::compareTo).orElse(null);
        LocalDate end = rows.stream().map(WarehouseDailyMetric::getDate).map(LocalDate::parse)
                .max(LocalDate::compareTo).orElse(null);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("period", start == null ? "" : start + " 至 " + end);
        meta.put("latestDate", end == null ? null : end.toString());
        meta.put("dayCount", rows.stream().map(WarehouseDailyMetric::getDate).distinct().count());
        meta.put("source", "MySQL · warehouse_dashboard");
        meta.put("warehouseCount", warehouseCount);
        meta.put("availableZoneRows", zones.size());
        meta.put("availableExceptionRows", alerts.size());
        return meta;
    }

    private List<InventorySnapshotEntity> loadInventoryRows(String warehouseId) {
        return inventorySnapshotMapper.selectList(Wrappers.lambdaQuery(InventorySnapshotEntity.class)
                .eq(InventorySnapshotEntity::getWarehouseId, warehouseId)
                .orderByAsc(InventorySnapshotEntity::getProjectNo, InventorySnapshotEntity::getMaterialCode));
    }

    private List<Map<String, Object>> mapInventory(List<InventorySnapshotEntity> rows, boolean grouped) {
        if (!grouped) {
            return rows.stream().map(source -> mapOf(
                    "materialCode", source.getMaterialCode(), "materialName", source.getMaterialName(), "projectNo", source.getProjectNo(),
                    "sku", source.getProjectMaterialSku(), "productIndex", source.getProductIndexNo(), "size", source.getGloveSize(),
                    "colorCode", source.getColorCode(), "unit", source.getMainUom(), "specification", source.getSpecification(),
                    "model", source.getModel(), "onHand", number(source.getOnHandMainQty()), "reserved", number(source.getReservedMainQty()),
                    "frozen", number(source.getFrozenMainQty()), "stockDate", source.getStockDate().toString()))
                    .collect(Collectors.toList());
        }
        Map<String, List<InventorySnapshotEntity>> groups = rows.stream().collect(Collectors.groupingBy(
                row -> row.getMaterialCode() + "\u0000" + row.getMaterialName() + "\u0000" + row.getMainUom(),
                LinkedHashMap::new, Collectors.toList()));
        return groups.values().stream().map(items -> {
            InventorySnapshotEntity first = items.get(0);
            double onHand = items.stream().mapToDouble(row -> number(row.getOnHandMainQty())).sum();
            double reserved = items.stream().mapToDouble(row -> number(row.getReservedMainQty())).sum();
            double frozen = items.stream().mapToDouble(row -> number(row.getFrozenMainQty())).sum();
            LocalDate stockDate = items.stream().map(InventorySnapshotEntity::getStockDate).max(LocalDate::compareTo).orElse(first.getStockDate());
            return mapOf("code", first.getMaterialCode(), "name", first.getMaterialName(), "uom", first.getMainUom(),
                    "specification", first.getSpecification(), "onHand", onHand, "reserved", reserved,
                    "frozen", frozen, "available", onHand - reserved - frozen,
                    "projects", items.stream().map(InventorySnapshotEntity::getProjectNo).distinct().count(), "stockDate", stockDate.toString());
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> loadDetailedAlerts(String warehouseId) {
        return exceptionEventMapper.selectList(Wrappers.lambdaQuery(ExceptionEventEntity.class)
                        .eq(ExceptionEventEntity::getWarehouseId, warehouseId).orderByDesc(ExceptionEventEntity::getEventTime)).stream()
                .map(source -> {
                    java.time.LocalDateTime occurred = source.getEventTime();
                    java.time.LocalDateTime closed = source.getCloseTime();
                    Integer duration = source.getDurationMinutes();
                    return mapOf("id", source.getEventId(), "occurredAt", occurred.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                            "time", occurred.toLocalTime().toString(), "type", source.getEventType(), "project", source.getProjectName(),
                            "materialCode", source.getMaterialCode(), "material", source.getMaterialName(), "category", source.getMaterialCategory(),
                            "areaCode", source.getAreaId(), "zoneCode", source.getAreaId(), "area", source.getAreaName(), "zone", source.getAreaName(),
                            "severity", source.getSeverity(), "status", source.getHandlingStatus(), "owner", source.getOwner(),
                            "responseMinutes", source.getResponseMinutes(), "slaHours", number(source.getSlaHours()),
                            "closedAt", closed == null ? null : closed.toString(), "durationMinutes", duration,
                            "durationHours", duration == null ? null : duration / 60d,
                            "slaBreached", Boolean.TRUE.equals(source.getIsSlaBreached()), "rootCause", source.getRootCause(), "action", source.getActionTaken());
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> dailyMap(WarehouseDailyMetric row) {
        boolean raw = "原料库".equals(row.getWarehouseType());
        boolean packaging = "箱盒库".equals(row.getWarehouseType());
        Number inbound = raw ? row.getRawInboundTon() : packaging ? row.getPackagingInboundPiece() : row.getFinishedInboundCarton();
        Number outbound = raw ? row.getRawOutboundTon() : packaging ? row.getPackagingOutboundPiece() : row.getFinishedOutboundCarton();
        return mapOf("date", row.getDate(), "inboundOrders", row.getInboundOrders(), "outboundOrders", row.getOutboundOrders(),
                "rawInbound", row.getRawInboundTon(), "rawOutbound", row.getRawOutboundTon(), "finishedInbound", row.getFinishedInboundCarton(),
                "finishedOutbound", row.getFinishedOutboundCarton(), "packagingInbound", row.getPackagingInboundPiece(), "packagingOutbound", row.getPackagingOutboundPiece(),
                "inbound", inbound, "outbound", outbound, "picking", row.getPicking(), "pickingTasks", row.getPicking(), "forkliftTasks", row.getForkliftTasks(),
                "inventoryAccuracy", row.getInventoryAccuracy(), "receivingTimely", row.getReceivingTimely(), "deliveryTimely", row.getDeliveryTimely(),
                "exceptions", row.getExceptions(), "receiptMinutes", row.getReceiptMinutes(), "pickingMinutes", row.getPickingMinutes(),
                "dockUtilization", row.getDockUtilization(), "overtimeHours", row.getOvertimeHours());
    }

    private Map<String, Object> zoneMap(Zone zone) {
        return mapOf("snapshotDate", zone.getSnapshotDate(), "code", zone.getCode(), "name", zone.getName(), "capacity", zone.getCapacity(),
                "occupied", zone.getOccupied(), "available", zone.getAvailable(), "occupancy", zone.getOccupancy(), "materialTypes", zone.getMaterialTypes(),
                "abnormal", zone.getAbnormal(), "abnormalLocations", zone.getAbnormal(), "frozen", zone.getFrozen(), "frozenQty", zone.getFrozen(),
                "owner", zone.getManager(), "status", zone.getStatus());
    }

    private Map<String, Object> targetMap(Target target) {
        return mapOf("key", target.getKey(), "name", target.getName(), "target", target.getTarget(), "unit", target.getUnit(),
                "rule", target.getRule(), "definition", target.getDefinition(), "source", target.getSource(),
                "direction", target.getRule() != null && target.getRule().contains("高于") ? "max" : "min");
    }

    private Map<String, Object> buildWarehouseSummary(List<WarehouseDailyMetric> rows, List<Zone> zones, List<Map<String, Object>> alerts, List<Map<String, Object>> stocks) {
        WarehouseDailyMetric latest = rows.isEmpty() ? new WarehouseDailyMetric() : rows.get(rows.size() - 1);
        int capacity = zones.stream().mapToInt(Zone::getCapacity).sum();
        int occupied = zones.stream().mapToInt(Zone::getOccupied).sum();
        int available = zones.stream().mapToInt(Zone::getAvailable).sum();
        int abnormal = zones.stream().mapToInt(Zone::getAbnormal).sum();
        long open = alerts.stream().filter(row -> !"已关闭".equals(row.get("status"))).count();
        long critical = alerts.stream().filter(row -> !"已关闭".equals(row.get("status")) && "紧急".equals(row.get("severity"))).count();
        long closed = alerts.size() - open;
        double onHand = stocks.stream().mapToDouble(row -> number(row.get("onHand"))).sum();
        double reserved = stocks.stream().mapToDouble(row -> number(row.get("reserved"))).sum();
        double frozen = stocks.stream().mapToDouble(row -> number(row.get("frozen"))).sum();
        double occupancy = capacity == 0 ? 0 : (double) occupied / capacity;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("todayRawInbound", latest.getRawInboundTon());
        summary.put("todayRawOutbound", latest.getRawOutboundTon());
        summary.put("todayInbound", dailyMap(latest).get("inbound"));
        summary.put("todayOutbound", dailyMap(latest).get("outbound"));
        summary.put("todayInboundOrders", latest.getInboundOrders());
        summary.put("todayOutboundOrders", latest.getOutboundOrders());
        summary.put("monthRawInbound", rows.stream().mapToDouble(WarehouseDailyMetric::getRawInboundTon).sum());
        summary.put("monthRawOutbound", rows.stream().mapToDouble(WarehouseDailyMetric::getRawOutboundTon).sum());
        summary.put("monthInboundOrders", rows.stream().mapToInt(WarehouseDailyMetric::getInboundOrders).sum());
        summary.put("monthOutboundOrders", rows.stream().mapToInt(WarehouseDailyMetric::getOutboundOrders).sum());
        summary.put("monthPickingTasks", rows.stream().mapToInt(DailyMetric::getPicking).sum());
        summary.put("monthForkliftTasks", rows.stream().mapToInt(DailyMetric::getForkliftTasks).sum());
        summary.put("inventoryAccuracy", average(rows, DailyMetric::getInventoryAccuracy));
        summary.put("receivingTimely", average(rows, DailyMetric::getReceivingTimely));
        summary.put("deliveryTimely", average(rows, DailyMetric::getDeliveryTimely));
        summary.put("avgReceiptMinutes", average(rows, DailyMetric::getReceiptMinutes));
        summary.put("avgPickingMinutes", average(rows, DailyMetric::getPickingMinutes));
        summary.put("dockUtilization", average(rows, DailyMetric::getDockUtilization));
        summary.put("overtimeHours", rows.stream().mapToDouble(DailyMetric::getOvertimeHours).sum());
        summary.put("occupancy", occupancy);
        summary.put("occupiedLocations", occupied);
        summary.put("availableLocations", available);
        summary.put("abnormalLocations", abnormal);
        summary.put("stockOnHandTon", onHand);
        summary.put("stockAvailableTon", onHand - reserved - frozen);
        summary.put("stockReservedTon", reserved);
        summary.put("stockFrozenTon", frozen);
        summary.put("exceptionTotal", alerts.size());
        summary.put("openExceptions", open);
        summary.put("criticalOpenExceptions", critical);
        summary.put("slaBreached", alerts.stream().filter(row -> Boolean.TRUE.equals(row.get("slaBreached"))).count());
        summary.put("exceptionCloseRate", alerts.isEmpty() ? 1d : (double) closed / alerts.size());
        summary.putAll(health(latest, occupancy, open));
        return summary;
    }

    private Map<String, Object> buildSummary(List<DailyMetric> rows, List<Zone> zones, List<Alert> alerts) {
        DailyMetric latest = rows.isEmpty() ? new DailyMetric() : rows.get(rows.size() - 1);
        int occupied = zones.stream().mapToInt(Zone::getOccupied).sum();
        int capacity = zones.stream().mapToInt(Zone::getCapacity).sum();
        int available = zones.stream().mapToInt(Zone::getAvailable).sum();
        int frozen = zones.stream().mapToInt(Zone::getFrozen).sum();
        long openAlerts = alerts.stream().filter(alert -> !"已关闭".equals(alert.getStatus())).count();
        long criticalAlerts = alerts.stream().filter(alert -> !"已关闭".equals(alert.getStatus()) && "紧急".equals(alert.getSeverity())).count();
        long closedAlerts = alerts.stream().filter(alert -> "已关闭".equals(alert.getStatus())).count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("latestDate", latest.getDate());
        summary.put("todayInbound", latest.getInbound());
        summary.put("todayOutbound", latest.getOutbound());
        summary.put("todayPicking", latest.getPicking());
        summary.put("monthInbound", sumInt(rows, DailyMetric::getInbound));
        summary.put("monthOutbound", sumInt(rows, DailyMetric::getOutbound));
        summary.put("monthPicking", sumInt(rows, DailyMetric::getPicking));
        summary.put("monthForkliftTasks", sumInt(rows, DailyMetric::getForkliftTasks));
        summary.put("inventoryAccuracy", average(rows, DailyMetric::getInventoryAccuracy));
        summary.put("receivingTimely", average(rows, DailyMetric::getReceivingTimely));
        summary.put("deliveryTimely", average(rows, DailyMetric::getDeliveryTimely));
        summary.put("dockUtilization", average(rows, DailyMetric::getDockUtilization));
        summary.put("avgReceiptMinutes", Math.round(average(rows, DailyMetric::getReceiptMinutes)));
        summary.put("avgPickingMinutes", Math.round(average(rows, DailyMetric::getPickingMinutes)));
        summary.put("overtimeHours", rows.stream().mapToDouble(DailyMetric::getOvertimeHours).sum());
        summary.put("totalLocations", capacity);
        summary.put("occupiedLocations", occupied);
        summary.put("availableLocations", available);
        summary.put("frozenLocations", frozen);
        double occupancy = capacity == 0 ? 0 : (double) occupied / capacity;
        summary.put("occupancy", occupancy);
        summary.put("openAlerts", openAlerts);
        summary.put("criticalAlerts", criticalAlerts);
        summary.put("exceptionCloseRate", alerts.isEmpty() ? 1 : (double) closedAlerts / alerts.size());
        summary.putAll(health(latest, occupancy, openAlerts));
        summary.put("deltas", calculateDeltas(rows));
        return summary;
    }

    private Map<String, Object> health(DailyMetric latest, double occupancy, long openAlerts) {
        int attention = 0;
        double penalty = 0;
        if (latest.getInventoryAccuracy() < .98) { attention++; penalty += (.98 - latest.getInventoryAccuracy()) * 300; }
        if (latest.getReceivingTimely() < .95) { attention++; penalty += (.95 - latest.getReceivingTimely()) * 250; }
        if (latest.getDeliveryTimely() < .94) { attention++; penalty += (.94 - latest.getDeliveryTimely()) * 250; }
        if (occupancy > .85) { attention++; penalty += (occupancy - .85) * 100; }
        if (openAlerts > 10) { attention++; penalty += (openAlerts - 10) * .8; }
        if (latest.getPickingMinutes() > 45) { attention++; penalty += (latest.getPickingMinutes() - 45) * .3; }
        int score = (int) Math.round(Math.max(0, Math.min(100, 100 - penalty)));
        return mapOf("healthScore", score, "healthLabel", score >= 92 ? "稳健运行" : score >= 82 ? "总体可控" : "需要干预", "attentionCount", attention);
    }

    private Map<String, Double> calculateDeltas(List<DailyMetric> rows) {
        Map<String, Double> deltas = new LinkedHashMap<>();
        int window = Math.min(7, rows.size() / 2);
        if (window == 0) {
            for (String key : new String[]{"inbound", "outbound", "inventoryAccuracy", "receivingTimely", "deliveryTimely", "occupancy", "exceptions"}) deltas.put(key, 0d);
            return deltas;
        }
        List<DailyMetric> current = rows.subList(rows.size() - window, rows.size());
        List<DailyMetric> previous = rows.subList(rows.size() - window * 2, rows.size() - window);
        deltas.put("inbound", relative(sumInt(current, DailyMetric::getInbound), sumInt(previous, DailyMetric::getInbound)));
        deltas.put("outbound", relative(sumInt(current, DailyMetric::getOutbound), sumInt(previous, DailyMetric::getOutbound)));
        deltas.put("inventoryAccuracy", relative(average(current, DailyMetric::getInventoryAccuracy), average(previous, DailyMetric::getInventoryAccuracy)));
        deltas.put("receivingTimely", relative(average(current, DailyMetric::getReceivingTimely), average(previous, DailyMetric::getReceivingTimely)));
        deltas.put("deliveryTimely", relative(average(current, DailyMetric::getDeliveryTimely), average(previous, DailyMetric::getDeliveryTimely)));
        deltas.put("occupancy", 0d);
        deltas.put("exceptions", relative(average(current, DailyMetric::getExceptions), average(previous, DailyMetric::getExceptions)));
        return deltas;
    }

    private Map<String, Object> exceptionBreakdown(List<Map<String, Object>> alerts) {
        return mapOf("byType", groupedCounts(alerts, "type"), "bySeverity", groupedCounts(alerts, "severity"), "byArea", groupedCounts(alerts, "area"));
    }

    private List<Map<String, Object>> groupedCounts(List<Map<String, Object>> rows, String key) {
        Map<Object, Long> counts = rows.stream().collect(Collectors.groupingBy(row -> row.get(key), Collectors.counting()));
        return counts.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(entry -> mapOf("name", entry.getKey(), "value", entry.getValue())).collect(Collectors.toList());
    }

    private static List<String> splitOwners(String owners) {
        if (owners == null || owners.trim().isEmpty()) return new ArrayList<>();
        return java.util.Arrays.asList(owners.split("[、,，]"));
    }

    private static String targetKey(String name) {
        if ("库存准确率".equals(name)) return "inventoryAccuracy";
        if ("入库及时率".equals(name)) return "receivingTimely";
        if ("出库及时率".equals(name)) return "deliveryTimely";
        if ("库区占用率".equals(name)) return "occupancy";
        if ("未关闭异常数".equals(name)) return "openExceptions";
        if ("异常关闭率".equals(name)) return "exceptionCloseRate";
        if ("平均拣货时长".equals(name)) return "avgPickingMinutes";
        return name;
    }

    private static double number(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : value == null ? 0 : Double.parseDouble(String.valueOf(value));
    }

    private static int sumInt(List<? extends DailyMetric> rows, ToIntFunction<DailyMetric> mapper) { return rows.stream().mapToInt(mapper).sum(); }
    private static double average(List<? extends DailyMetric> rows, ToDoubleFunction<DailyMetric> mapper) { return rows.stream().mapToDouble(mapper).average().orElse(0); }
    private static double relative(double current, double previous) { return previous == 0 ? 0 : (current - previous) / previous * 100; }


    private static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) map.put(String.valueOf(pairs[index]), pairs[index + 1]);
        return map;
    }
}
