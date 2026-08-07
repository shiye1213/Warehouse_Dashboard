package com.intco.warehouse.service;
import com.intco.warehouse.entity.WarehouseEntity;
import com.intco.warehouse.mapper.WarehouseMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseImportService {
    private static final int MAX_IMPORT_ROWS = 10000;
    private static final String AGE_RULE_SHEET = "\u5e93\u9f84\u89c4\u5219";
    private static final String AGE_BATCH_SHEET = "\u5e93\u9f84\u6279\u6b21\u660e\u7ec6";
    private static final String AGE_SKU_SHEET = "\u5e93\u9f84SKU\u6c47\u603b";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final List<DateTimeFormatter> DATE_TIMES = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    private final WarehouseMapper warehouseMapper;

    public WarehouseImportService(WarehouseMapper warehouseMapper) {
        this.warehouseMapper = warehouseMapper;
    }

    public boolean isEmpty() {
        return warehouseMapper.selectCount(null) == 0;
    }

    @Transactional
    public ImportSummary importWorkbook(InputStream input, String fileName) throws IOException {
        String importId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();
        try (Workbook workbook = WorkbookFactory.create(input)) {
            requireSheets(workbook);
            Map<String, String> warehouseRoles = readWarehouseRoles(workbook);
            List<Object[]> warehouses = readWarehouses(workbook, warehouseRoles);
            if (warehouses.isEmpty()) {
                throw new IllegalArgumentException("仓库主数据中没有可导入的数据行");
            }

            clearBusinessTables();
            insertRows(warehouses, warehouseMapper::insertWarehouses);

            int importedRows = warehouses.size();
            importedRows += importInventory(workbook);
            importedRows += importSkuDaily(workbook);
            importedRows += importWarehouseDaily(workbook);
            importedRows += importAreaSnapshots(workbook);
            importedRows += importExceptions(workbook);
            importedRows += importBom(workbook);
            importedRows += importTargets(workbook);
            if (hasInventoryAgeSheets(workbook)) {
                importedRows += importInventoryAgeRules(workbook);
                importedRows += importInventoryAgeBatches(workbook);
                importedRows += importInventoryAgeSkus(workbook);
            }

            writeImportJob(importId, fileName, "FULL_WORKBOOK", importedRows, startedAt, "SUCCESS", "完整数据集已原子替换");
            LocalDate[] range = dateRange();
            return new ImportSummary(importId, "FULL_WORKBOOK", importedRows, range[0], range[1]);
        }
    }

    @Transactional
    public ImportSummary importWarehouseDailyCsv(InputStream input, String fileName) throws IOException {
        String importId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();
        List<Object[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true).setTrim(true).build().parse(reader)) {
            Map<String, String> headers = canonicalCsvHeaders(parser.getHeaderMap().keySet());
            if (!headers.containsValue("biz_date")) {
                throw new IllegalArgumentException("CSV 缺少 biz_date（业务日期）字段");
            }
            for (CSVRecord record : parser) {
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    values.put(header.getValue(), record.get(header.getKey()));
                }
                rows.add(csvDailyRow(values, (int) record.getRecordNumber() + 1));
                if (rows.size() > MAX_IMPORT_ROWS) {
                    throw new IllegalArgumentException("单次导入最多支持 10,000 行");
                }
            }
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV 中没有可导入的数据行");

        for (Object[] row : rows) {
            warehouseMapper.deleteWarehouseDaily(row[0], row[1]);
        }
        insertRows(rows, warehouseMapper::insertWarehouseDaily);
        writeImportJob(importId, fileName, "WAREHOUSE_DAILY_CSV", rows.size(), startedAt, "SUCCESS", "仓库日指标已按日期和仓库合并");
        LocalDate[] range = dateRange();
        return new ImportSummary(importId, "WAREHOUSE_DAILY_CSV", rows.size(), range[0], range[1]);
    }

    private void requireSheets(Workbook workbook) {
        String[] names = {"仓库主数据", "现存量快照", "运营_SKU日指标", "运营_仓库每日指标", "运营_库区状态", "运营_异常事件", "项目_BOM关系", "运营_KPI目标"};
        List<String> missing = new ArrayList<>();
        for (String name : names) if (workbook.getSheet(name) == null) missing.add(name);
        for (String name : new String[]{AGE_RULE_SHEET, AGE_BATCH_SHEET, AGE_SKU_SHEET}) if (workbook.getSheet(name) == null) missing.add(name);
        if (!missing.isEmpty()) throw new IllegalArgumentException("Excel 缺少工作表：" + String.join("、", missing));
    }

    private boolean hasInventoryAgeSheets(Workbook workbook) {
        return workbook.getSheet(AGE_RULE_SHEET) != null && workbook.getSheet(AGE_BATCH_SHEET) != null
                && workbook.getSheet(AGE_SKU_SHEET) != null;
    }

    private Map<String, String> readWarehouseRoles(Workbook workbook) {
        Table table = Table.open(workbook, "运营_SKU日指标", "warehouse_id");
        Map<String, String> roles = new HashMap<>();
        table.forEach(row -> roles.putIfAbsent(table.text(row, "warehouse_id"), table.text(row, "warehouse_role")));
        return roles;
    }

    private List<Object[]> readWarehouses(Workbook workbook, Map<String, String> roles) {
        Table table = Table.open(workbook, "仓库主数据", "warehouse_id");
        List<Object[]> rows = new ArrayList<>();
        table.forEach(row -> {
            String id = table.requiredText(row, "warehouse_id");
            rows.add(new Object[]{id, table.requiredText(row, "warehouse_name"), table.requiredText(row, "warehouse_type"),
                    roles.get(id), table.integer(row, "area_count"), table.integer(row, "capacity_locations"), table.text(row, "warehouse_owner")});
        });
        return rows;
    }

    private int importInventory(Workbook workbook) {
        Table table = Table.open(workbook, "现存量快照", "material_code");
        Map<String, String> warehouseIds = warehouseIdsByName();
        List<Object[]> rows = new ArrayList<>();
        table.forEach(row -> rows.add(new Object[]{
                requiredWarehouseId(warehouseIds, table.requiredText(row, "warehouse_name")),
                table.requiredText(row, "material_code"), table.requiredText(row, "project_no"), table.sqlDate(row, "stock_date"),
                table.requiredText(row, "warehouse_name"), table.requiredText(row, "material_name"), table.text(row, "customer_item"),
                table.requiredText(row, "project_material_sku"), table.text(row, "product_index_no"), table.text(row, "glove_size"),
                table.text(row, "color_code"), table.requiredText(row, "main_uom"), table.text(row, "specification"), table.text(row, "model"),
                table.decimal(row, "on_hand_main_qty"), table.decimal(row, "reserved_main_qty"), table.decimal(row, "frozen_main_qty"),
                table.decimal(row, "vendor_owned_on_hand_main_qty")
        }));
        insertRows(rows, warehouseMapper::insertInventory);
        return rows.size();
    }

    private int importSkuDaily(Workbook workbook) {
        Table table = Table.open(workbook, "运营_SKU日指标", "biz_date");
        List<Object[]> rows = new ArrayList<>();
        table.forEach(row -> rows.add(new Object[]{
                table.sqlDate(row, "biz_date"), table.requiredText(row, "warehouse_id"), table.requiredText(row, "warehouse_name"),
                table.requiredText(row, "warehouse_type"), table.text(row, "warehouse_role"), table.requiredText(row, "project_no"),
                table.requiredText(row, "project_name"), table.requiredText(row, "material_code"), table.requiredText(row, "material_name"),
                table.requiredText(row, "project_material_sku"), table.requiredText(row, "warehouse_sku_key"), table.requiredText(row, "material_category"),
                table.text(row, "color"), table.text(row, "model"), table.requiredText(row, "uom"), table.text(row, "packaging_level"),
                table.requiredText(row, "area_id"), table.requiredText(row, "area_name"), table.integer(row, "inbound_order_count"),
                table.integer(row, "inbound_line_count"), table.decimal(row, "inbound_qty"), table.integer(row, "outbound_order_count"),
                table.integer(row, "outbound_line_count"), table.decimal(row, "outbound_qty"), table.integer(row, "picking_task_count"),
                table.integer(row, "forklift_task_count"), table.nullableDecimal(row, "inventory_accuracy"), table.nullableDecimal(row, "receipt_timely_rate"),
                table.nullableDecimal(row, "delivery_timely_rate"), table.nullableDecimal(row, "avg_receipt_minutes"),
                table.nullableDecimal(row, "avg_picking_minutes"), table.integer(row, "exception_count"), table.nullableDecimal(row, "avg_outbound_lead_days")
        }));
        insertRows(rows, warehouseMapper::insertSkuDaily);
        return rows.size();
    }

    private int importWarehouseDaily(Workbook workbook) {
        Table table = Table.open(workbook, "运营_仓库每日指标", "biz_date");
        List<Object[]> rows = new ArrayList<>();
        table.forEach(row -> rows.add(warehouseDailyRow(table, row)));
        insertRows(rows, warehouseMapper::insertWarehouseDaily);
        return rows.size();
    }

    private Object[] warehouseDailyRow(Table table, Row row) {
        return new Object[]{table.sqlDate(row, "biz_date"), table.requiredText(row, "warehouse_id"), table.requiredText(row, "warehouse_name"),
                table.requiredText(row, "warehouse_type"), table.integer(row, "inbound_order_count"), table.integer(row, "outbound_order_count"),
                table.decimal(row, "raw_inbound_ton"), table.decimal(row, "raw_outbound_ton"), table.integer(row, "finished_inbound_carton"),
                table.integer(row, "finished_outbound_carton"), table.integer(row, "packaging_inbound_piece"), table.integer(row, "packaging_outbound_piece"),
                table.integer(row, "picking_task_count"), table.integer(row, "forklift_task_count"), table.decimal(row, "inventory_accuracy"),
                table.decimal(row, "receipt_timely_rate"), table.decimal(row, "delivery_timely_rate"), table.integer(row, "exception_count"),
                table.decimal(row, "avg_receipt_minutes"), table.decimal(row, "avg_picking_minutes"), table.decimal(row, "dock_utilization_rate"),
                table.decimal(row, "overtime_hours")};
    }

    private int importAreaSnapshots(Workbook workbook) {
        Table table = Table.open(workbook, "运营_库区状态", "snapshot_date");
        List<Object[]> rows = new ArrayList<>();
        table.forEach(row -> rows.add(new Object[]{table.sqlDate(row, "snapshot_date"), table.requiredText(row, "warehouse_id"),
                table.requiredText(row, "warehouse_name"), table.requiredText(row, "warehouse_type"), table.requiredText(row, "area_id"),
                table.requiredText(row, "area_name"), table.integer(row, "capacity_locations"), table.integer(row, "occupied_locations"),
                table.integer(row, "available_locations"), table.decimal(row, "occupancy_rate"), table.integer(row, "material_type_count"),
                table.integer(row, "abnormal_location_count"), table.decimal(row, "frozen_qty"), table.text(row, "area_owner"), table.requiredText(row, "status")}));
        insertRows(rows, warehouseMapper::insertAreaSnapshots);
        return rows.size();
    }

    private int importExceptions(Workbook workbook) {
        Table table = Table.open(workbook, "运营_异常事件", "event_id");
        List<Object[]> rows = new ArrayList<>();
        table.forEach(row -> rows.add(new Object[]{table.requiredText(row, "event_id"), table.sqlTimestamp(row, "event_time"),
                table.requiredText(row, "event_type"), table.requiredText(row, "warehouse_id"), table.requiredText(row, "warehouse_name"),
                table.requiredText(row, "warehouse_type"), table.text(row, "project_no"), table.text(row, "project_name"), table.text(row, "material_code"),
                table.text(row, "material_name"), table.text(row, "project_material_sku"), table.text(row, "material_category"), table.text(row, "color"),
                table.text(row, "model"), table.text(row, "uom"), table.text(row, "packaging_level"), table.text(row, "area_id"), table.text(row, "area_name"),
                table.requiredText(row, "severity"), table.requiredText(row, "handling_status"), table.text(row, "owner"), table.nullableInteger(row, "response_minutes"),
                table.nullableDecimal(row, "sla_hours"), table.nullableTimestamp(row, "deadline_time"), table.nullableTimestamp(row, "close_time"),
                table.nullableInteger(row, "duration_minutes"), table.yesNo(row, "is_sla_breached"), table.text(row, "root_cause"),
                table.text(row, "action_taken"), table.text(row, "remark")}));
        insertRows(rows, warehouseMapper::insertExceptions);
        return rows.size();
    }

    private int importBom(Workbook workbook) {
        Table table = Table.open(workbook, "项目_BOM关系", "project_no");
        List<Object[]> rows = new ArrayList<>();
        table.forEach(row -> rows.add(new Object[]{table.requiredText(row, "project_no"), table.requiredText(row, "project_name"),
                table.requiredText(row, "finished_material_code"), table.requiredText(row, "finished_material_name"), table.text(row, "finished_color"),
                table.text(row, "finished_model"), table.requiredText(row, "finished_uom"), table.requiredText(row, "component_category"),
                table.requiredText(row, "component_material_code"), table.requiredText(row, "component_material_name"), table.text(row, "component_color"),
                table.text(row, "component_model"), table.requiredText(row, "component_uom"), table.decimal(row, "component_qty_per_finished_carton"),
                table.requiredText(row, "component_qty_uom"), table.text(row, "bom_relationship")}));
        insertRows(rows, warehouseMapper::insertBom);
        return rows.size();
    }

    private int importTargets(Workbook workbook) {
        Table table = Table.open(workbook, "运营_KPI目标", "kpi_name");
        List<Object[]> rows = new ArrayList<>();
        table.forEach(row -> rows.add(new Object[]{table.requiredText(row, "kpi_name"), table.decimal(row, "target_value"),
                table.requiredText(row, "unit"), table.requiredText(row, "warning_rule"), table.text(row, "calculation_definition"), table.text(row, "data_source")}));
        insertRows(rows, warehouseMapper::insertTargets);
        return rows.size();
    }

    private int importInventoryAgeRules(Workbook workbook) {
        Table table = Table.open(workbook, AGE_RULE_SHEET, "rule_type");
        List<Object[]> rows = new ArrayList<>();
        table.forEach(row -> rows.add(new Object[]{
                table.requiredText(row, "rule_type"), table.requiredText(row, "rule_name"),
                table.requiredText(row, "rule_condition"), table.requiredText(row, "result_level"),
                table.text(row, "action_guidance"), table.text(row, "applicable_scope")
        }));
        insertRows(rows, warehouseMapper::insertInventoryAgeRules);
        return rows.size();
    }

    private int importInventoryAgeBatches(Workbook workbook) {
        Table table = Table.open(workbook, AGE_BATCH_SHEET, "age_batch_id");
        List<Object[]> rows = new ArrayList<>();
        table.forEach(row -> rows.add(new Object[]{
                table.sqlDate(row, "snapshot_date"), table.requiredText(row, "age_batch_id"),
                table.requiredText(row, "warehouse_id"), table.requiredText(row, "warehouse_name"),
                table.requiredText(row, "warehouse_type"), table.requiredText(row, "project_no"),
                table.requiredText(row, "project_name"), table.requiredText(row, "material_code"),
                table.requiredText(row, "material_name"), table.requiredText(row, "project_material_sku"),
                table.requiredText(row, "material_category"), table.text(row, "color"), table.text(row, "model"),
                table.requiredText(row, "uom"), table.requiredText(row, "batch_no"),
                table.sqlDate(row, "receipt_date"), table.integer(row, "age_days"),
                table.requiredText(row, "age_bucket"), table.decimal(row, "batch_on_hand_qty"),
                table.decimal(row, "batch_reserved_qty"), table.decimal(row, "batch_frozen_qty"),
                table.decimal(row, "available_qty"), table.nullableDecimal(row, "unit_cost"),
                table.nullableDecimal(row, "inventory_amount"), table.nullableSqlDate(row, "last_outbound_date"),
                table.nullableInteger(row, "days_since_last_outbound"), table.decimal(row, "outbound_qty_30d"),
                table.nullableDecimal(row, "outbound_rate_30d"), table.nullableDecimal(row, "coverage_days"),
                table.text(row, "movement_status"), table.requiredText(row, "stagnant_level"),
                table.yesNo(row, "is_stagnant"), table.decimal(row, "stagnant_score"),
                table.text(row, "priority"), table.text(row, "recommended_action"),
                table.text(row, "owner"), table.text(row, "data_source")
        }));
        insertRows(rows, warehouseMapper::insertInventoryAgeBatches);
        return rows.size();
    }

    private int importInventoryAgeSkus(Workbook workbook) {
        Table table = Table.open(workbook, AGE_SKU_SHEET, "project_material_sku");
        List<Object[]> rows = new ArrayList<>();
        table.forEach(row -> rows.add(new Object[]{
                table.sqlDate(row, "snapshot_date"), table.requiredText(row, "warehouse_id"),
                table.requiredText(row, "warehouse_name"), table.requiredText(row, "warehouse_type"),
                table.requiredText(row, "project_no"), table.requiredText(row, "project_name"),
                table.requiredText(row, "material_code"), table.requiredText(row, "material_name"),
                table.requiredText(row, "project_material_sku"), table.requiredText(row, "material_category"),
                table.text(row, "color"), table.text(row, "model"), table.requiredText(row, "uom"),
                table.integer(row, "batch_count"), table.decimal(row, "on_hand_qty"),
                table.decimal(row, "available_qty"), table.nullableDecimal(row, "inventory_amount"),
                table.nullableDecimal(row, "weighted_avg_age_days"), table.integer(row, "max_age_days"),
                table.text(row, "dominant_age_bucket"), table.decimal(row, "outbound_qty_30d"),
                table.nullableDecimal(row, "outbound_rate_30d"),
                table.nullableSqlDate(row, "latest_sku_outbound_date"),
                table.nullableInteger(row, "days_since_last_sku_outbound"),
                table.integer(row, "stagnant_batch_count"),
                table.nullableDecimal(row, "stagnant_inventory_amount"),
                table.nullableDecimal(row, "stagnation_ratio"), table.requiredText(row, "stagnant_level"),
                table.yesNo(row, "is_stagnant"), table.decimal(row, "stagnant_score"),
                table.text(row, "priority"), table.text(row, "recommended_action"), table.text(row, "owner")
        }));
        insertRows(rows, warehouseMapper::insertInventoryAgeSkus);
        return rows.size();
    }

    private void insertRows(List<Object[]> rows, java.util.function.ToIntFunction<Object[]> inserter) {
        for (Object[] row : rows) inserter.applyAsInt(row);
    }

    private void clearBusinessTables() {
        warehouseMapper.clearExceptionEvents();
        warehouseMapper.clearAreaSnapshots();
        warehouseMapper.clearWarehouseDaily();
        warehouseMapper.clearSkuDaily();
        warehouseMapper.clearInventory();
        warehouseMapper.clearBom();
        warehouseMapper.clearTargets();
        warehouseMapper.clearInventoryAgeBatches();
        warehouseMapper.clearInventoryAgeSkus();
        warehouseMapper.clearInventoryAgeRules();
        warehouseMapper.clearWarehouses();
    }

    private Map<String, String> warehouseIdsByName() {
        Map<String, String> result = new HashMap<>();
        for (WarehouseEntity warehouse : warehouseMapper.selectList(null)) {
            result.put(warehouse.getWarehouseName(), warehouse.getWarehouseId());
        }
        return result;
    }

    private String requiredWarehouseId(Map<String, String> ids, String name) {
        String id = ids.get(name);
        if (id == null) throw new IllegalArgumentException("现存量快照引用了未知仓库：" + name);
        return id;
    }

    private void writeImportJob(String id, String fileName, String type, int rows, LocalDateTime startedAt, String status, String message) {
        warehouseMapper.insertImportJob(id, fileName == null ? "" : fileName, type, rows, startedAt, LocalDateTime.now(), status, message);
    }

    private LocalDate[] dateRange() {
        Map<String, Object> meta = warehouseMapper.selectMeta(null);
        Date start = (Date) mapValue(meta, "start_date");
        Date end = (Date) mapValue(meta, "end_date");
        return new LocalDate[]{start == null ? null : start.toLocalDate(), end == null ? null : end.toLocalDate()};
    }

    private static Object mapValue(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet())
            if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        return null;
    }

    private Map<String, String> canonicalCsvHeaders(Iterable<String> sourceHeaders) {
        Map<String, String> aliases = csvAliases();
        Map<String, String> result = new LinkedHashMap<>();
        for (String source : sourceHeaders) {
            String normalized = source.replace("\uFEFF", "").trim();
            String canonical = aliases.get(normalized);
            if (canonical != null) result.put(source, canonical);
        }
        return result;
    }

    private Object[] csvDailyRow(Map<String, String> values, int rowNumber) {
        try {
            String warehouseId = value(values, "warehouse_id", "WH-FG03");
            WarehouseEntity warehouse = warehouseMapper.selectById(warehouseId);
            if (warehouse == null) throw new IllegalArgumentException("?????" + warehouseId);
            return new Object[]{Date.valueOf(parseDate(value(values, "biz_date", null))), warehouseId,
                    value(values, "warehouse_name", warehouse.getWarehouseName()),
                    value(values, "warehouse_type", warehouse.getWarehouseType()),
                    integer(values, "inbound_order_count"), integer(values, "outbound_order_count"), decimal(values, "raw_inbound_ton"),
                    decimal(values, "raw_outbound_ton"), integer(values, "finished_inbound_carton"), integer(values, "finished_outbound_carton"),
                    integer(values, "packaging_inbound_piece"), integer(values, "packaging_outbound_piece"), integer(values, "picking_task_count"),
                    integer(values, "forklift_task_count"), rate(values, "inventory_accuracy"), rate(values, "receipt_timely_rate"),
                    rate(values, "delivery_timely_rate"), integer(values, "exception_count"), decimal(values, "avg_receipt_minutes"),
                    decimal(values, "avg_picking_minutes"), rate(values, "dock_utilization_rate"), decimal(values, "overtime_hours")};
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("CSV 第 " + rowNumber + " 行：" + error.getMessage(), error);
        }
    }

    private static Map<String, String> csvAliases() {
        Map<String, String> map = new HashMap<>();
        String[][] aliases = {
                {"biz_date", "日期", "业务日期"}, {"warehouse_id", "仓库编码"}, {"warehouse_name", "仓库名称"}, {"warehouse_type", "仓库类型"},
                {"inbound_order_count", "入库单数"}, {"outbound_order_count", "出库单数"}, {"raw_inbound_ton", "原材料入库量（吨）"},
                {"raw_outbound_ton", "原材料领用量（吨）"}, {"finished_inbound_carton", "成品入库量（箱）", "入库箱数"},
                {"finished_outbound_carton", "成品出库量（箱）", "出库箱数"}, {"packaging_inbound_piece", "包材入库量（个）"},
                {"packaging_outbound_piece", "包材领用量（个）"}, {"picking_task_count", "拣货任务数", "拣货任务"},
                {"forklift_task_count", "叉车任务数", "叉车任务"}, {"inventory_accuracy", "库存准确率"}, {"receipt_timely_rate", "入库及时率"},
                {"delivery_timely_rate", "出库及时率"}, {"exception_count", "异常数"}, {"avg_receipt_minutes", "平均收货时长", "收货时长(分钟)"},
                {"avg_picking_minutes", "平均拣货时长", "拣货时长(分钟)"}, {"dock_utilization_rate", "月台利用率"}, {"overtime_hours", "加班工时"}
        };
        for (String[] group : aliases) for (String alias : group) map.put(alias, group[0]);
        return map;
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static int integer(Map<String, String> values, String key) {
        String value = value(values, key, "0").replace(",", "");
        return new BigDecimal(value).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
    }

    private static BigDecimal decimal(Map<String, String> values, String key) {
        String value = value(values, key, "0").replace(",", "");
        return new BigDecimal(value);
    }

    private static BigDecimal rate(Map<String, String> values, String key) {
        String value = value(values, key, "0").replace(",", "");
        if (value.endsWith("%")) return new BigDecimal(value.substring(0, value.length() - 1)).divide(BigDecimal.valueOf(100));
        BigDecimal result = new BigDecimal(value);
        return result.compareTo(BigDecimal.ONE) > 0 ? result.divide(BigDecimal.valueOf(100)) : result;
    }

    private static LocalDate parseDate(String value) {
        if (value == null) throw new IllegalArgumentException("业务日期不能为空");
        try { return LocalDate.parse(value.trim(), DATE); }
        catch (DateTimeParseException error) { throw new IllegalArgumentException("业务日期格式应为 yyyy-MM-dd"); }
    }


    public static class ImportSummary {
        private final String importId;
        private final String importType;
        private final int importedRows;
        private final LocalDate startDate;
        private final LocalDate endDate;

        public ImportSummary(String importId, String importType, int importedRows, LocalDate startDate, LocalDate endDate) {
            this.importId = importId; this.importType = importType; this.importedRows = importedRows; this.startDate = startDate; this.endDate = endDate;
        }
        public String getImportId() { return importId; }
        public String getImportType() { return importType; }
        public int getImportedRows() { return importedRows; }
        public String getStartDate() { return startDate == null ? null : startDate.toString(); }
        public String getEndDate() { return endDate == null ? null : endDate.toString(); }
    }

    private static final class Table {
        private final Sheet sheet;
        private final int headerRow;
        private final Map<String, Integer> columns;
        private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

        private Table(Sheet sheet, int headerRow, Map<String, Integer> columns) {
            this.sheet = sheet; this.headerRow = headerRow; this.columns = columns;
        }

        static Table open(Workbook workbook, String sheetName, String requiredColumn) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) throw new IllegalArgumentException("缺少工作表：" + sheetName);
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= Math.min(sheet.getLastRowNum(), 10); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                Map<String, Integer> columns = new LinkedHashMap<>();
                for (Cell cell : row) {
                    String value = formatter.formatCellValue(cell).replace("\r", "").trim();
                    if (value.isEmpty()) continue;
                    String technical = value.split("\n", 2)[0].trim();
                    columns.put(technical, cell.getColumnIndex());
                }
                if (columns.containsKey(requiredColumn)) return new Table(sheet, rowIndex, columns);
            }
            throw new IllegalArgumentException(sheetName + " 未找到字段行：" + requiredColumn);
        }

        void forEach(java.util.function.Consumer<Row> consumer) {
            int count = 0;
            for (int rowIndex = headerRow + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || blank(row)) continue;
                consumer.accept(row);
                count++;
                if (count > MAX_IMPORT_ROWS) throw new IllegalArgumentException(sheet.getSheetName() + " 超过 10,000 行限制");
            }
        }

        String requiredText(Row row, String field) {
            String value = text(row, field);
            if (value.isEmpty()) throw new IllegalArgumentException(sheet.getSheetName() + " 第 " + (row.getRowNum() + 1) + " 行字段 " + field + " 不能为空");
            return value;
        }

        String text(Row row, String field) {
            Cell cell = cell(row, field);
            return cell == null ? "" : formatter.formatCellValue(cell).trim();
        }

        int integer(Row row, String field) { return decimal(row, field).setScale(0, java.math.RoundingMode.HALF_UP).intValue(); }
        Integer nullableInteger(Row row, String field) { BigDecimal value = nullableDecimal(row, field); return value == null ? null : value.setScale(0, java.math.RoundingMode.HALF_UP).intValue(); }
        BigDecimal decimal(Row row, String field) { BigDecimal value = nullableDecimal(row, field); return value == null ? BigDecimal.ZERO : value; }

        BigDecimal nullableDecimal(Row row, String field) {
            Cell cell = cell(row, field);
            if (cell == null || cell.getCellType() == CellType.BLANK) return null;
            if (cell.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(cell.getNumericCellValue());
            String value = formatter.formatCellValue(cell).replace(",", "").trim();
            if (value.isEmpty() || "-".equals(value)) return null;
            if (value.endsWith("%")) return new BigDecimal(value.substring(0, value.length() - 1)).divide(BigDecimal.valueOf(100));
            return new BigDecimal(value);
        }

        Date sqlDate(Row row, String field) {
            Cell cell = cell(row, field);
            if (cell == null) throw new IllegalArgumentException(field + " 不能为空");
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
                return Date.valueOf(cell.getLocalDateTimeCellValue().toLocalDate());
            }
            return Date.valueOf(parseDate(formatter.formatCellValue(cell)));
        }
        Date nullableSqlDate(Row row, String field) {
            Cell cell = cell(row, field);
            if (cell == null || cell.getCellType() == CellType.BLANK) return null;
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
                return Date.valueOf(cell.getLocalDateTimeCellValue().toLocalDate());
            }
            String value = formatter.formatCellValue(cell).trim();
            return value.isEmpty() || "-".equals(value) ? null : Date.valueOf(parseDate(value));
        }


        Timestamp sqlTimestamp(Row row, String field) {
            Timestamp value = nullableTimestamp(row, field);
            if (value == null) throw new IllegalArgumentException(field + " 不能为空");
            return value;
        }

        Timestamp nullableTimestamp(Row row, String field) {
            Cell cell = cell(row, field);
            if (cell == null || cell.getCellType() == CellType.BLANK) return null;
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
                return Timestamp.valueOf(cell.getLocalDateTimeCellValue());
            }
            String value = formatter.formatCellValue(cell).trim();
            if (value.isEmpty()) return null;
            for (DateTimeFormatter format : DATE_TIMES) {
                try { return Timestamp.valueOf(LocalDateTime.parse(value, format)); } catch (DateTimeParseException ignored) { }
            }
            throw new IllegalArgumentException(field + " 时间格式无效：" + value);
        }

        boolean yesNo(Row row, String field) {
            String value = text(row, field);
            return "是".equals(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
        }

        private Cell cell(Row row, String field) {
            Integer index = columns.get(field);
            if (index == null) throw new IllegalArgumentException(sheet.getSheetName() + " 缺少字段：" + field);
            return row.getCell(index);
        }

        private boolean blank(Row row) {
            for (Integer index : columns.values()) {
                Cell cell = row.getCell(index);
                if (cell != null && !formatter.formatCellValue(cell).trim().isEmpty()) return false;
            }
            return true;
        }
    }
}
