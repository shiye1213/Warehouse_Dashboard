package com.intco.warehouse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intco.warehouse.model.DashboardData;
import com.intco.warehouse.model.DashboardData.Alert;
import com.intco.warehouse.model.DashboardData.DailyMetric;
import com.intco.warehouse.model.DashboardData.WarehouseDailyMetric;
import com.intco.warehouse.model.DashboardData.Zone;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
public class WarehouseDataService {
    private final ObjectMapper objectMapper;
    private DashboardData data;

    public WarehouseDataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public synchronized void loadSeedData() throws IOException {
        ClassPathResource resource = new ClassPathResource("data/dashboard-data.js");
        String script = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        int assignment = script.indexOf('=');
        if (assignment < 0) {
            throw new IllegalStateException("Seed data format is invalid");
        }
        String json = script.substring(assignment + 1).trim();
        if (json.endsWith(";")) {
            json = json.substring(0, json.length() - 1);
        }
        data = objectMapper.readValue(json, DashboardData.class);
        normalizeDaily(data.getDaily());
        loadWarehouseDaily();
    }

    public synchronized DashboardData currentData() {
        return objectMapper.convertValue(data, DashboardData.class);
    }

    public synchronized Map<String, Object> snapshot(int requestedRange) {
        int range = Math.max(1, Math.min(requestedRange, 366));
        List<DailyMetric> allRows = new ArrayList<>(data.getDaily());
        normalizeDaily(allRows);
        int start = Math.max(0, allRows.size() - range);
        List<DailyMetric> trend = new ArrayList<>(allRows.subList(start, allRows.size()));

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>(data.getMeta());
        if (!allRows.isEmpty()) {
            meta.put("latestDate", allRows.get(allRows.size() - 1).getDate());
            meta.put("period", allRows.get(0).getDate() + " 至 " + allRows.get(allRows.size() - 1).getDate());
        }
        result.put("meta", meta);
        result.put("summary", buildSummary(allRows));
        result.put("trend", trend);
        result.put("warehouseDaily", data.getWarehouseDaily());
        result.put("zones", data.getZones());
        result.put("alerts", data.getAlerts());
        result.put("targets", data.getTargets());
        result.put("forklifts", data.getForklifts());
        return result;
    }

    private void loadWarehouseDaily() throws IOException {
        ClassPathResource source = new ClassPathResource("data/warehouse-data.xlsx");
        if (!source.exists()) return;
        List<WarehouseDailyMetric> rows = new ArrayList<>();
        try (InputStream input = source.getInputStream(); Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheet("运营_仓库每日指标");
            if (sheet == null) return;
            for (int index = 3; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || row.getCell(0) == null) continue;
                WarehouseDailyMetric item = new WarehouseDailyMetric();
                item.setDate(row.getCell(0).getLocalDateTimeCellValue().toLocalDate().toString());
                item.setWarehouseId(text(row, 1));
                item.setWarehouseName(text(row, 2));
                item.setWarehouseType(text(row, 3));
                item.setInboundOrders(integer(row, 4));
                item.setOutboundOrders(integer(row, 5));
                item.setRawInboundTon(number(row, 6));
                item.setRawOutboundTon(number(row, 7));
                item.setFinishedInboundCarton(integer(row, 8));
                item.setFinishedOutboundCarton(integer(row, 9));
                item.setPackagingInboundPiece(integer(row, 10));
                item.setPackagingOutboundPiece(integer(row, 11));
                item.setPicking(integer(row, 12));
                item.setForkliftTasks(integer(row, 13));
                item.setInventoryAccuracy(number(row, 14));
                item.setReceivingTimely(number(row, 15));
                item.setDeliveryTimely(number(row, 16));
                item.setExceptions(integer(row, 17));
                item.setReceiptMinutes(number(row, 18));
                item.setPickingMinutes(number(row, 19));
                item.setDockUtilization(number(row, 20));
                item.setOvertimeHours(number(row, 21));
                rows.add(item);
            }
            loadZones(workbook);
            loadAlerts(workbook);
        }
        rows.sort(Comparator.comparing(WarehouseDailyMetric::getDate).thenComparing(WarehouseDailyMetric::getWarehouseName));
        data.setWarehouseDaily(rows);
    }

    private void loadZones(Workbook workbook) {
        Sheet sheet = workbook.getSheet("运营_库区状态");
        if (sheet == null) return;
        LocalDate latest = null;
        for (int index = 3; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null || row.getCell(0) == null) continue;
            LocalDate date = row.getCell(0).getLocalDateTimeCellValue().toLocalDate();
            if (latest == null || date.isAfter(latest)) latest = date;
        }
        if (latest == null) return;
        List<Zone> zones = new ArrayList<>();
        for (int index = 3; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null || row.getCell(0) == null
                    || !latest.equals(row.getCell(0).getLocalDateTimeCellValue().toLocalDate())) continue;
            Zone item = new Zone();
            item.setSnapshotDate(latest.toString());
            item.setWarehouse(text(row, 2));
            item.setCode(text(row, 4));
            item.setName(text(row, 5));
            item.setCapacity(integer(row, 6));
            item.setOccupied(integer(row, 7));
            item.setAvailable(integer(row, 8));
            item.setOccupancy(number(row, 9));
            item.setMaterialTypes(integer(row, 10));
            item.setAbnormal(integer(row, 11));
            item.setFrozen(integer(row, 12));
            item.setManager(text(row, 13));
            item.setStatus(text(row, 14));
            zones.add(item);
        }
        data.setZones(zones);
        data.getMeta().put("zoneSnapshotDate", latest.toString());
        data.getMeta().put("availableZoneRows", zones.size());
    }

    private void loadAlerts(Workbook workbook) {
        Sheet sheet = workbook.getSheet("运营_异常事件");
        if (sheet == null) return;
        List<Alert> alerts = new ArrayList<>();
        for (int index = 3; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null || row.getCell(0) == null) continue;
            LocalDateTime occurredAt = row.getCell(1).getLocalDateTimeCellValue();
            Alert item = new Alert();
            item.setId(text(row, 0));
            item.setDate(occurredAt.toLocalDate().toString());
            item.setTime(occurredAt.toLocalTime().toString());
            item.setType(text(row, 2));
            item.setWarehouse(text(row, 4));
            item.setZoneCode(text(row, 16));
            item.setZone(text(row, 17));
            item.setTitle(item.getType() + " · " + item.getZone());
            item.setSeverity(text(row, 18));
            item.setStatus(text(row, 19));
            item.setOwner(text(row, 20));
            item.setResponseMinutes(integer(row, 21));
            item.setSlaHours(number(row, 22));
            item.setDurationHours(number(row, 25) / 60d);
            item.setSlaBreached("是".equals(text(row, 26)));
            if (row.getCell(24) != null && row.getCell(24).getLocalDateTimeCellValue() != null)
                item.setClosedAt(row.getCell(24).getLocalDateTimeCellValue().toString());
            item.setDescription("根因：" + text(row, 27));
            item.setRecommendation(text(row, 28));
            item.setMaterial(text(row, 9));
            item.setProject(text(row, 7));
            alerts.add(item);
        }
        data.setAlerts(alerts);
        data.getMeta().put("availableExceptionRows", alerts.size());
    }

    private static String text(Row row, int column) {
        return row.getCell(column) == null ? "" : row.getCell(column).getStringCellValue();
    }

    private static double number(Row row, int column) {
        return row.getCell(column) == null ? 0 : row.getCell(column).getNumericCellValue();
    }

    private static int integer(Row row, int column) {
        return (int) Math.round(number(row, column));
    }

    public synchronized Optional<Map<String, Object>> zoneDetail(String code) {
        return data.getZones().stream()
                .filter(zone -> zone.getCode().equalsIgnoreCase(code))
                .findFirst()
                .map(zone -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("zone", zone);
                    detail.put("relatedAlerts", data.getAlerts().stream()
                            .filter(alert -> code.equalsIgnoreCase(nullSafe(alert.getZoneCode())) || zone.getName().equals(alert.getZone()))
                            .collect(Collectors.toList()));
                    return detail;
                });
    }

    public synchronized ImportResult mergeDailyMetrics(List<DailyMetric> importedRows) {
        if (importedRows == null || importedRows.isEmpty()) {
            throw new IllegalArgumentException("文件中没有可导入的日指标数据");
        }
        Map<String, DailyMetric> byDate = new LinkedHashMap<>();
        for (DailyMetric row : data.getDaily()) {
            byDate.put(row.getDate(), row);
        }
        for (DailyMetric row : importedRows) {
            byDate.put(row.getDate(), row);
        }
        List<DailyMetric> merged = new ArrayList<>(byDate.values());
        normalizeDaily(merged);
        data.setDaily(merged);
        data.getMeta().put("source", "用户导入数据 + 系统基础数据");
        data.getMeta().put("latestDate", merged.get(merged.size() - 1).getDate());
        data.getMeta().put("period", merged.get(0).getDate() + " 至 " + merged.get(merged.size() - 1).getDate());
        return new ImportResult(importedRows.size(), merged.get(0).getDate(), merged.get(merged.size() - 1).getDate());
    }

    private Map<String, Object> buildSummary(List<DailyMetric> rows) {
        Map<String, Object> summary = new LinkedHashMap<>();
        DailyMetric latest = rows.isEmpty() ? new DailyMetric() : rows.get(rows.size() - 1);
        int occupied = data.getZones().stream().mapToInt(Zone::getOccupied).sum();
        int capacity = data.getZones().stream().mapToInt(Zone::getCapacity).sum();
        int available = data.getZones().stream().mapToInt(Zone::getAvailable).sum();
        int frozen = data.getZones().stream().mapToInt(Zone::getFrozen).sum();
        long openAlerts = data.getAlerts().stream().filter(alert -> !"已关闭".equals(alert.getStatus())).count();
        long criticalAlerts = data.getAlerts().stream().filter(alert -> !"已关闭".equals(alert.getStatus()) && "紧急".equals(alert.getSeverity())).count();
        long closedAlerts = data.getAlerts().stream().filter(alert -> "已关闭".equals(alert.getStatus())).count();

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
        summary.put("occupancy", capacity == 0 ? 0 : (double) occupied / capacity);
        summary.put("openAlerts", openAlerts);
        summary.put("criticalAlerts", criticalAlerts);
        summary.put("exceptionCloseRate", data.getAlerts().isEmpty() ? 1 : (double) closedAlerts / data.getAlerts().size());

        Map<String, Object> health = health(latest, capacity == 0 ? 0 : (double) occupied / capacity, openAlerts);
        summary.putAll(health);
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("healthScore", score);
        result.put("healthLabel", score >= 92 ? "稳健运行" : score >= 82 ? "总体可控" : "需要干预");
        result.put("attentionCount", attention);
        return result;
    }

    private Map<String, Double> calculateDeltas(List<DailyMetric> rows) {
        Map<String, Double> deltas = new LinkedHashMap<>();
        int window = Math.min(7, rows.size() / 2);
        if (window == 0) {
            deltas.put("inbound", 0d);
            deltas.put("outbound", 0d);
            deltas.put("inventoryAccuracy", 0d);
            deltas.put("receivingTimely", 0d);
            deltas.put("deliveryTimely", 0d);
            deltas.put("occupancy", 0d);
            deltas.put("exceptions", 0d);
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
        deltas.put("exceptions", relative(average(current, row -> row.getExceptions()), average(previous, row -> row.getExceptions())));
        return deltas;
    }

    private static int sumInt(List<DailyMetric> rows, ToIntFunction<DailyMetric> mapper) {
        return rows.stream().mapToInt(mapper).sum();
    }

    private static double average(List<DailyMetric> rows, ToDoubleFunction<DailyMetric> mapper) {
        return rows.stream().mapToDouble(mapper).average().orElse(0);
    }

    private static double relative(double current, double previous) {
        return previous == 0 ? 0 : (current - previous) / previous * 100;
    }

    private static void normalizeDaily(List<DailyMetric> rows) {
        rows.sort(Comparator.comparing(row -> LocalDate.parse(row.getDate())));
    }

    private static String nullSafe(String value) { return value == null ? "" : value; }

    public static class ImportResult {
        private final int importedRows;
        private final String startDate;
        private final String endDate;
        public ImportResult(int importedRows, String startDate, String endDate) {
            this.importedRows = importedRows;
            this.startDate = startDate;
            this.endDate = endDate;
        }
        public int getImportedRows() { return importedRows; }
        public String getStartDate() { return startDate; }
        public String getEndDate() { return endDate; }
    }
}
