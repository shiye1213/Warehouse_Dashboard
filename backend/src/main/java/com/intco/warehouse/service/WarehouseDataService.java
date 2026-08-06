package com.intco.warehouse.service;

import com.intco.warehouse.model.DashboardData;
import com.intco.warehouse.model.DashboardData.Alert;
import com.intco.warehouse.model.DashboardData.DailyMetric;
import com.intco.warehouse.model.DashboardData.Target;
import com.intco.warehouse.model.DashboardData.WarehouseDailyMetric;
import com.intco.warehouse.model.DashboardData.Zone;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WarehouseDataService {
    private final JdbcTemplate jdbc;

    public WarehouseDataService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DashboardData currentData() {
        DashboardData data = new DashboardData();
        data.setWarehouseDaily(loadWarehouseDaily(null));
        data.setDaily(aggregateDaily(data.getWarehouseDaily()));
        data.setZones(loadLatestZones(null));
        data.setAlerts(loadAlerts(null));
        data.setTargets(loadTargets());
        data.setMeta(loadMeta(null));
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
        return jdbc.query("SELECT warehouse_id,warehouse_name,warehouse_type,warehouse_role,area_count,capacity_locations,warehouse_owner FROM warehouse ORDER BY warehouse_id",
                (rs, rowNum) -> mapOf(
                        "warehouseId", rs.getString("warehouse_id"), "warehouseName", rs.getString("warehouse_name"),
                        "warehouseType", rs.getString("warehouse_type"), "warehouseRole", rs.getString("warehouse_role"),
                        "areaCount", rs.getInt("area_count"), "capacityLocations", rs.getInt("capacity_locations"),
                        "warehouseOwner", rs.getString("warehouse_owner")));
    }

    public Optional<Map<String, Object>> warehouseSnapshot(String warehouseId, int requestedRange) {
        Map<String, Object> warehouse;
        try {
            warehouse = jdbc.queryForObject("SELECT warehouse_id,warehouse_name,warehouse_type,warehouse_role,area_count,capacity_locations,warehouse_owner FROM warehouse WHERE warehouse_id=?",
                    (rs, rowNum) -> mapOf(
                            "warehouseId", rs.getString("warehouse_id"), "warehouseName", rs.getString("warehouse_name"),
                            "warehouseType", rs.getString("warehouse_type"), "warehouseRole", rs.getString("warehouse_role"),
                            "areaCount", rs.getInt("area_count"), "capacityLocations", rs.getInt("capacity_locations"),
                            "owners", splitOwners(rs.getString("warehouse_owner"))), warehouseId);
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }

        List<WarehouseDailyMetric> allDaily = loadWarehouseDaily(warehouseId);
        int range = Math.max(1, Math.min(requestedRange, 366));
        List<WarehouseDailyMetric> daily = new ArrayList<>(allDaily.subList(Math.max(0, allDaily.size() - range), allDaily.size()));
        List<Zone> zones = loadLatestZones(warehouseId);
        List<Alert> alerts = loadAlerts(warehouseId);
        List<Map<String, Object>> inventory = loadInventory(warehouseId, false);
        List<Map<String, Object>> stocks = loadInventory(warehouseId, true);
        List<Target> targets = loadTargets();

        LocalDate start = allDaily.isEmpty() ? null : LocalDate.parse(allDaily.get(0).getDate());
        LocalDate end = allDaily.isEmpty() ? null : LocalDate.parse(allDaily.get(allDaily.size() - 1).getDate());
        warehouse.put("period", start == null ? "" : start + " 至 " + end);
        warehouse.put("latestDate", end == null ? null : end.toString());
        warehouse.put("snapshotDate", zones.isEmpty() ? null : zones.get(0).getSnapshotDate());
        warehouse.put("source", "MySQL · warehouse_dashboard");

        List<Map<String, Object>> dailyMaps = daily.stream().map(this::dailyMap).collect(Collectors.toList());
        List<Map<String, Object>> alertMaps = loadDetailedAlerts(warehouseId);
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
        result.put("targets", targets.stream().map(this::targetMap).collect(Collectors.toList()));
        result.put("exceptionBreakdown", exceptionBreakdown(alertMaps));
        return Optional.of(result);
    }

    public Optional<Map<String, Object>> zoneDetail(String code) {
        List<Zone> matches = jdbc.query("SELECT snapshot_date,warehouse_name,area_id,area_name,capacity_locations,occupied_locations,available_locations,occupancy_rate,material_type_count,abnormal_location_count,frozen_qty,area_owner,status FROM warehouse_area_snapshot WHERE area_id=? ORDER BY snapshot_date DESC LIMIT 1",
                this::mapZone, code);
        if (matches.isEmpty()) return Optional.empty();
        Zone zone = matches.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("zone", zone);
        result.put("relatedAlerts", loadAlertsByArea(code));
        return Optional.of(result);
    }

    public Map<String, Object> dataStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("warehouses", scalar("SELECT COUNT(*) FROM warehouse"));
        result.put("inventorySnapshots", scalar("SELECT COUNT(*) FROM inventory_snapshot"));
        result.put("skuDailyMetrics", scalar("SELECT COUNT(*) FROM sku_daily_metric"));
        result.put("warehouseDailyMetrics", scalar("SELECT COUNT(*) FROM warehouse_daily_metric"));
        result.put("areaSnapshots", scalar("SELECT COUNT(*) FROM warehouse_area_snapshot"));
        result.put("exceptionEvents", scalar("SELECT COUNT(*) FROM exception_event"));
        result.put("bomRelations", scalar("SELECT COUNT(*) FROM bom_relation"));
        result.put("kpiTargets", scalar("SELECT COUNT(*) FROM kpi_target"));
        result.putAll(loadMeta(null));
        return result;
    }

    private int scalar(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private List<WarehouseDailyMetric> loadWarehouseDaily(String warehouseId) {
        String sql = "SELECT * FROM warehouse_daily_metric" + (warehouseId == null ? "" : " WHERE warehouse_id=?") + " ORDER BY biz_date,warehouse_id";
        return warehouseId == null ? jdbc.query(sql, this::mapWarehouseDaily) : jdbc.query(sql, this::mapWarehouseDaily, warehouseId);
    }

    private WarehouseDailyMetric mapWarehouseDaily(ResultSet rs, int rowNum) throws SQLException {
        WarehouseDailyMetric row = new WarehouseDailyMetric();
        row.setDate(rs.getDate("biz_date").toLocalDate().toString());
        row.setWarehouseId(rs.getString("warehouse_id"));
        row.setWarehouseName(rs.getString("warehouse_name"));
        row.setWarehouseType(rs.getString("warehouse_type"));
        row.setInboundOrders(rs.getInt("inbound_order_count"));
        row.setOutboundOrders(rs.getInt("outbound_order_count"));
        row.setRawInboundTon(rs.getDouble("raw_inbound_ton"));
        row.setRawOutboundTon(rs.getDouble("raw_outbound_ton"));
        row.setFinishedInboundCarton(rs.getInt("finished_inbound_carton"));
        row.setFinishedOutboundCarton(rs.getInt("finished_outbound_carton"));
        row.setPackagingInboundPiece(rs.getInt("packaging_inbound_piece"));
        row.setPackagingOutboundPiece(rs.getInt("packaging_outbound_piece"));
        row.setInbound(rs.getInt("finished_inbound_carton"));
        row.setOutbound(rs.getInt("finished_outbound_carton"));
        row.setPicking(rs.getInt("picking_task_count"));
        row.setForkliftTasks(rs.getInt("forklift_task_count"));
        row.setInventoryAccuracy(rs.getDouble("inventory_accuracy"));
        row.setReceivingTimely(rs.getDouble("receipt_timely_rate"));
        row.setDeliveryTimely(rs.getDouble("delivery_timely_rate"));
        row.setExceptions(rs.getInt("exception_count"));
        row.setReceiptMinutes(rs.getDouble("avg_receipt_minutes"));
        row.setPickingMinutes(rs.getDouble("avg_picking_minutes"));
        row.setAverageDuration((row.getReceiptMinutes() + row.getPickingMinutes()) / 2d);
        row.setDockUtilization(rs.getDouble("dock_utilization_rate"));
        row.setOvertimeHours(rs.getDouble("overtime_hours"));
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
        String filter = warehouseId == null ? "" : " AND s.warehouse_id=?";
        String sql = "SELECT s.snapshot_date,s.warehouse_name,s.area_id,s.area_name,s.capacity_locations,s.occupied_locations,s.available_locations,s.occupancy_rate,s.material_type_count,s.abnormal_location_count,s.frozen_qty,s.area_owner,s.status " +
                "FROM warehouse_area_snapshot s JOIN (SELECT warehouse_id,MAX(snapshot_date) max_date FROM warehouse_area_snapshot GROUP BY warehouse_id) latest " +
                "ON latest.warehouse_id=s.warehouse_id AND latest.max_date=s.snapshot_date WHERE 1=1" + filter + " ORDER BY s.warehouse_id,s.area_id";
        return warehouseId == null ? jdbc.query(sql, this::mapZone) : jdbc.query(sql, this::mapZone, warehouseId);
    }

    private Zone mapZone(ResultSet rs, int rowNum) throws SQLException {
        Zone zone = new Zone();
        zone.setSnapshotDate(rs.getDate("snapshot_date").toLocalDate().toString());
        zone.setWarehouse(rs.getString("warehouse_name"));
        zone.setCode(rs.getString("area_id"));
        zone.setName(rs.getString("area_name"));
        zone.setCapacity(rs.getInt("capacity_locations"));
        zone.setOccupied(rs.getInt("occupied_locations"));
        zone.setAvailable(rs.getInt("available_locations"));
        zone.setOccupancy(rs.getDouble("occupancy_rate"));
        zone.setMaterialTypes(rs.getInt("material_type_count"));
        zone.setAbnormal(rs.getInt("abnormal_location_count"));
        zone.setFrozen((int) Math.round(rs.getDouble("frozen_qty")));
        zone.setManager(rs.getString("area_owner"));
        zone.setStatus(rs.getString("status"));
        return zone;
    }

    private List<Alert> loadAlerts(String warehouseId) {
        String sql = "SELECT * FROM exception_event" + (warehouseId == null ? "" : " WHERE warehouse_id=?") + " ORDER BY event_time DESC";
        return warehouseId == null ? jdbc.query(sql, this::mapAlert) : jdbc.query(sql, this::mapAlert, warehouseId);
    }

    private List<Alert> loadAlertsByArea(String areaId) {
        return jdbc.query("SELECT * FROM exception_event WHERE area_id=? ORDER BY event_time DESC", this::mapAlert, areaId);
    }

    private Alert mapAlert(ResultSet rs, int rowNum) throws SQLException {
        Timestamp time = rs.getTimestamp("event_time");
        Alert alert = new Alert();
        alert.setId(rs.getString("event_id"));
        alert.setDate(time.toLocalDateTime().toLocalDate().toString());
        alert.setTime(time.toLocalDateTime().toLocalTime().toString());
        alert.setType(rs.getString("event_type"));
        alert.setWarehouse(rs.getString("warehouse_name"));
        alert.setZoneCode(rs.getString("area_id"));
        alert.setZone(rs.getString("area_name"));
        alert.setTitle(alert.getType() + " · " + alert.getZone());
        alert.setSeverity(rs.getString("severity"));
        alert.setStatus(rs.getString("handling_status"));
        alert.setOwner(rs.getString("owner"));
        alert.setResponseMinutes(rs.getInt("response_minutes"));
        alert.setSlaHours(rs.getDouble("sla_hours"));
        int duration = rs.getInt("duration_minutes");
        alert.setDurationHours(duration / 60d);
        alert.setSlaBreached(rs.getBoolean("is_sla_breached"));
        Timestamp closed = rs.getTimestamp("close_time");
        alert.setClosedAt(closed == null ? null : closed.toLocalDateTime().toString());
        alert.setDescription("根因：" + rs.getString("root_cause"));
        alert.setRecommendation(rs.getString("action_taken"));
        alert.setMaterial(rs.getString("material_name"));
        alert.setProject(rs.getString("project_name"));
        return alert;
    }

    private List<Target> loadTargets() {
        return jdbc.query("SELECT * FROM kpi_target ORDER BY kpi_name", (rs, rowNum) -> {
            Target target = new Target();
            target.setKey(targetKey(rs.getString("kpi_name")));
            target.setName(rs.getString("kpi_name"));
            target.setTarget(rs.getDouble("target_value"));
            target.setUnit(rs.getString("unit"));
            target.setRule(rs.getString("warning_rule"));
            target.setDefinition(rs.getString("calculation_definition"));
            target.setSource(rs.getString("data_source"));
            return target;
        });
    }

    private Map<String, Object> loadMeta(String warehouseId) {
        String sql = "SELECT MIN(biz_date) start_date,MAX(biz_date) end_date,COUNT(DISTINCT biz_date) day_count FROM warehouse_daily_metric" + (warehouseId == null ? "" : " WHERE warehouse_id=?");
        Map<String, Object> meta = warehouseId == null ? jdbc.queryForObject(sql, this::mapMeta) : jdbc.queryForObject(sql, this::mapMeta, warehouseId);
        meta.put("source", "MySQL · warehouse_dashboard");
        meta.put("warehouseCount", scalar("SELECT COUNT(*) FROM warehouse"));
        meta.put("availableZoneRows", loadLatestZones(warehouseId).size());
        meta.put("availableExceptionRows", loadAlerts(warehouseId).size());
        return meta;
    }

    private Map<String, Object> mapMeta(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> meta = new LinkedHashMap<>();
        Date start = rs.getDate("start_date");
        Date end = rs.getDate("end_date");
        meta.put("period", start == null ? "" : start.toLocalDate() + " 至 " + end.toLocalDate());
        meta.put("latestDate", end == null ? null : end.toLocalDate().toString());
        meta.put("dayCount", rs.getInt("day_count"));
        return meta;
    }

    private List<Map<String, Object>> loadInventory(String warehouseId, boolean grouped) {
        if (grouped) {
            return jdbc.query("SELECT material_code,material_name,main_uom,MAX(specification) specification,SUM(on_hand_main_qty) on_hand,SUM(reserved_main_qty) reserved,SUM(frozen_main_qty) frozen,COUNT(DISTINCT project_no) projects,MAX(stock_date) stock_date FROM inventory_snapshot WHERE warehouse_id=? GROUP BY material_code,material_name,main_uom ORDER BY material_code",
                    (rs, rowNum) -> mapOf("code", rs.getString("material_code"), "name", rs.getString("material_name"), "uom", rs.getString("main_uom"),
                            "specification", rs.getString("specification"), "onHand", rs.getBigDecimal("on_hand"), "reserved", rs.getBigDecimal("reserved"),
                            "frozen", rs.getBigDecimal("frozen"), "available", rs.getBigDecimal("on_hand").subtract(rs.getBigDecimal("reserved")).subtract(rs.getBigDecimal("frozen")),
                            "projects", rs.getInt("projects"), "stockDate", rs.getDate("stock_date").toLocalDate().toString()), warehouseId);
        }
        return jdbc.query("SELECT * FROM inventory_snapshot WHERE warehouse_id=? ORDER BY project_no,material_code", (rs, rowNum) -> mapOf(
                "materialCode", rs.getString("material_code"), "materialName", rs.getString("material_name"), "projectNo", rs.getString("project_no"),
                "sku", rs.getString("project_material_sku"), "productIndex", rs.getString("product_index_no"), "size", rs.getString("glove_size"),
                "colorCode", rs.getString("color_code"), "unit", rs.getString("main_uom"), "specification", rs.getString("specification"),
                "model", rs.getString("model"), "onHand", rs.getBigDecimal("on_hand_main_qty"), "reserved", rs.getBigDecimal("reserved_main_qty"),
                "frozen", rs.getBigDecimal("frozen_main_qty"), "stockDate", rs.getDate("stock_date").toLocalDate().toString()), warehouseId);
    }

    private List<Map<String, Object>> loadDetailedAlerts(String warehouseId) {
        return jdbc.query("SELECT * FROM exception_event WHERE warehouse_id=? ORDER BY event_time DESC", (rs, rowNum) -> {
            Timestamp occurred = rs.getTimestamp("event_time");
            Timestamp closed = rs.getTimestamp("close_time");
            return mapOf("id", rs.getString("event_id"), "occurredAt", occurred.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    "time", occurred.toLocalDateTime().toLocalTime().toString(), "type", rs.getString("event_type"), "project", rs.getString("project_name"),
                    "materialCode", rs.getString("material_code"), "material", rs.getString("material_name"), "category", rs.getString("material_category"),
                    "areaCode", rs.getString("area_id"), "zoneCode", rs.getString("area_id"), "area", rs.getString("area_name"), "zone", rs.getString("area_name"),
                    "severity", rs.getString("severity"), "status", rs.getString("handling_status"), "owner", rs.getString("owner"),
                    "responseMinutes", rs.getInt("response_minutes"), "slaHours", rs.getDouble("sla_hours"),
                    "closedAt", closed == null ? null : closed.toLocalDateTime().toString(), "durationMinutes", rs.getObject("duration_minutes"),
                    "durationHours", rs.getObject("duration_minutes") == null ? null : rs.getInt("duration_minutes") / 60d,
                    "slaBreached", rs.getBoolean("is_sla_breached"), "rootCause", rs.getString("root_cause"), "action", rs.getString("action_taken"));
        }, warehouseId);
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
