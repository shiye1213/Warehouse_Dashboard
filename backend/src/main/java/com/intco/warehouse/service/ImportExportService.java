package com.intco.warehouse.service;
import com.intco.warehouse.mapper.WarehouseMapper;

import com.intco.warehouse.service.WarehouseImportService.ImportSummary;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportExportService {
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String AGE_RULE_SHEET = "\u5e93\u9f84\u89c4\u5219";
    private static final String AGE_BATCH_SHEET = "\u5e93\u9f84\u6279\u6b21\u660e\u7ec6";
    private static final String AGE_SKU_SHEET = "\u5e93\u9f84SKU\u6c47\u603b";
    private final WarehouseImportService importService;
    private final WarehouseDataService dataService;
    private final WarehouseMapper warehouseMapper;

    public ImportExportService(WarehouseImportService importService, WarehouseDataService dataService, WarehouseMapper warehouseMapper) {
        this.importService = importService;
        this.dataService = dataService;
        this.warehouseMapper = warehouseMapper;
    }

    public ImportSummary importFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要导入的文件");
        String originalName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String filename = originalName.toLowerCase(Locale.ROOT);
        if (filename.endsWith(".xlsx")) return importService.importWorkbook(file.getInputStream(), originalName);
        if (filename.endsWith(".csv")) return importService.importWarehouseDailyCsv(file.getInputStream(), originalName);
        throw new IllegalArgumentException("仅支持 .xlsx 或 UTF-8 .csv 文件");
    }

    public ExportFile export(String format) throws IOException {
        if ("csv".equalsIgnoreCase(format)) {
            return new ExportFile("warehouse-daily-metrics.csv", "text/csv;charset=UTF-8", exportDailyCsv());
        }
        if (!"xlsx".equalsIgnoreCase(format)) throw new IllegalArgumentException("导出格式仅支持 xlsx 或 csv");
        return new ExportFile("warehouse-operation-dataset.xlsx", XLSX_CONTENT_TYPE, exportWorkbook(false));
    }

    public ExportFile template() throws IOException {
        return new ExportFile("warehouse-import-template.xlsx", XLSX_CONTENT_TYPE, exportWorkbook(true));
    }

    public Map<String, Object> status() {
        return dataService.dataStatus();
    }

    private byte[] exportWorkbook(boolean template) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (template) writeInstructions(workbook);
            for (Dataset dataset : datasets()) {
                List<Map<String, Object>> rows = template ? new ArrayList<>() : queryRows(dataset);
                writeDataset(workbook, dataset, rows);
            }
            workbook.setActiveSheet(0);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] exportDailyCsv() {
        Dataset dataset = warehouseDailyDataset();
        StringBuilder csv = new StringBuilder("\uFEFF");
        for (int i = 0; i < dataset.columns.length; i++) {
            if (i > 0) csv.append(',');
            csv.append(dataset.columns[i].technical);
        }
        csv.append("\r\n");
        for (Map<String, Object> row : queryRows(dataset)) {
            for (int i = 0; i < dataset.columns.length; i++) {
                if (i > 0) csv.append(',');
                Object value = get(row, dataset.columns[i].technical);
                csv.append(csvEscape(value));
            }
            csv.append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void writeInstructions(Workbook workbook) {
        Sheet sheet = workbook.createSheet("导入说明");
        sheet.createRow(0).createCell(0).setCellValue("仓库运营数据导入模板");
        sheet.createRow(2).createCell(0).setCellValue("1. 完整 Excel 导入必须保留全部 11 个数据工作表及第 3 行技术字段名。数据会在单个事务中校验并替换。 ");
        sheet.createRow(3).createCell(0).setCellValue("2. 日期使用 yyyy-MM-dd，时间使用 yyyy-MM-dd HH:mm:ss，百分比可填写 0.98 或 98%。");
        sheet.createRow(4).createCell(0).setCellValue("3. CSV 仅用于仓库日指标，字段名使用运营_仓库每日指标中的技术字段名。");
        sheet.setColumnWidth(0, 110 * 256);
    }

    private void writeDataset(Workbook workbook, Dataset dataset, List<Map<String, Object>> rows) {
        Sheet sheet = workbook.createSheet(dataset.sheetName);
        CellStyle titleStyle = titleStyle(workbook);
        CellStyle noteStyle = noteStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle dateStyle = dateStyle(workbook, "yyyy-mm-dd");
        CellStyle dateTimeStyle = dateStyle(workbook, "yyyy-mm-dd hh:mm:ss");
        CellStyle percentStyle = numberStyle(workbook, "0.0%");
        CellStyle decimalStyle = numberStyle(workbook, "#,##0.0000");
        CellStyle integerStyle = numberStyle(workbook, "#,##0");
        CellStyle textStyle = numberStyle(workbook, "@");

        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue(dataset.title);
        title.getCell(0).setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, dataset.columns.length - 1));
        Row note = sheet.createRow(1);
        note.createCell(0).setCellValue(dataset.description);
        note.getCell(0).setCellStyle(noteStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, dataset.columns.length - 1));

        Row header = sheet.createRow(2);
        for (int columnIndex = 0; columnIndex < dataset.columns.length; columnIndex++) {
            Column column = dataset.columns[columnIndex];
            Cell cell = header.createCell(columnIndex);
            cell.setCellValue(column.technical + "\n" + column.chinese);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(columnIndex, Math.min(32, Math.max(12, Math.max(column.technical.length(), column.chinese.length()) + 2)) * 256);
        }
        header.setHeightInPoints(34);

        int rowIndex = 3;
        for (Map<String, Object> values : rows) {
            Row row = sheet.createRow(rowIndex++);
            for (int columnIndex = 0; columnIndex < dataset.columns.length; columnIndex++) {
                Column column = dataset.columns[columnIndex];
                Object value = get(values, column.technical);
                Cell cell = row.createCell(columnIndex);
                writeValue(cell, value);
                if (value instanceof String) cell.setCellStyle(textStyle);
                else if (value instanceof Date || value instanceof LocalDate) cell.setCellStyle(dateStyle);
                else if (value instanceof Timestamp || value instanceof LocalDateTime) cell.setCellStyle(dateTimeStyle);
                else if (column.percent) cell.setCellStyle(percentStyle);
                else if (value instanceof BigDecimal || value instanceof Double || value instanceof Float) cell.setCellStyle(decimalStyle);
                else if (value instanceof Number) cell.setCellStyle(integerStyle);
            }
        }
        sheet.createFreezePane(0, 3);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(2, Math.max(2, rowIndex - 1), 0, dataset.columns.length - 1));
    }

    private static void writeValue(Cell cell, Object value) {
        if (value == null) return;
        if (value instanceof Number) cell.setCellValue(((Number) value).doubleValue());
        else if (value instanceof Boolean) cell.setCellValue((Boolean) value ? "是" : "否");
        else if (value instanceof Date) cell.setCellValue((Date) value);
        else if (value instanceof Timestamp) cell.setCellValue((Timestamp) value);
        else if (value instanceof LocalDate) cell.setCellValue(Date.valueOf((LocalDate) value));
        else if (value instanceof LocalDateTime) cell.setCellValue(Timestamp.valueOf((LocalDateTime) value));
        else cell.setCellValue(String.valueOf(value));
    }

    private static Object get(Map<String, Object> row, String key) {
        if (row.containsKey(key)) return row.get(key);
        for (Map.Entry<String, Object> entry : row.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        return null;
    }

    private static String csvEscape(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) return "\"" + text.replace("\"", "\"\"") + "\"";
        return text;
    }

    private static CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex()); font.setFontHeightInPoints((short) 14);
        style.setFont(font); style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        return style;
    }

    private static CellStyle noteStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont(); font.setItalic(true); font.setColor(IndexedColors.GREY_50_PERCENT.getIndex()); style.setFont(font);
        return style;
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT); style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER); style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN); style.setBorderTop(BorderStyle.THIN);
        Font font = workbook.createFont(); font.setBold(true); style.setFont(font);
        return style;
    }

    private static CellStyle dateStyle(Workbook workbook, String format) {
        CellStyle style = workbook.createCellStyle(); style.setDataFormat(workbook.createDataFormat().getFormat(format)); return style;
    }

    private static CellStyle numberStyle(Workbook workbook, String format) {
        CellStyle style = workbook.createCellStyle(); style.setDataFormat(workbook.createDataFormat().getFormat(format)); return style;
    }

    private List<Map<String, Object>> queryRows(Dataset dataset) {
        if ("AGE_RULE".equals(dataset.query)) return warehouseMapper.exportInventoryAgeRules();
        if ("AGE_BATCH".equals(dataset.query)) return warehouseMapper.exportInventoryAgeBatches();
        if ("AGE_SKU".equals(dataset.query)) return warehouseMapper.exportInventoryAgeSkus();
        switch (dataset.columns[0].technical) {
            case "warehouse_id": return warehouseMapper.exportWarehouses();
            case "warehouse_name": return warehouseMapper.exportInventory();
            case "biz_date": return dataset.columns.length == 33 ? warehouseMapper.exportSkuDaily() : warehouseMapper.exportWarehouseDaily();
            case "snapshot_date": return warehouseMapper.exportAreaSnapshots();
            case "event_id": return warehouseMapper.exportExceptions();
            case "project_no": return warehouseMapper.exportBom();
            case "kpi_name": return warehouseMapper.exportTargets();
            default: throw new IllegalArgumentException("???????");
        }
    }

    private List<Dataset> datasets() {
        return Arrays.asList(warehouseDataset(), inventoryDataset(), skuDailyDataset(), warehouseDailyDataset(),
                areaDataset(), exceptionDataset(), bomDataset(), targetDataset(),
                inventoryAgeRuleDataset(), inventoryAgeBatchDataset(), inventoryAgeSkuDataset());
    }

    private Dataset warehouseDataset() {
        return new Dataset("仓库主数据", "仓库主数据", "三个运营看板共用的仓库筛选与容量主数据。",
                "WAREHOUSE",
                c("warehouse_id", "仓库编码"), c("warehouse_name", "仓库名称"), c("warehouse_type", "仓库类型"), c("area_count", "库区数量"), c("capacity_locations", "容量库位数"), c("warehouse_owner", "仓库负责人"));
    }

    private Dataset inventoryDataset() {
        return new Dataset("现存量快照", "现存量快照", "按仓库、项目、物料和库存日期保存的数量快照。",
                "INVENTORY",
                c("warehouse_name","仓库名称"),c("material_code","物料编码"),c("material_name","物料名称"),c("project_no","项目号"),c("customer_item","客户 ITEM"),c("project_material_sku","项目物料 SKU"),c("product_index_no","产品索引号"),c("glove_size","手套型号"),c("color_code","颜色代码"),c("main_uom","主计量单位"),c("specification","规格"),c("model","型号"),c("on_hand_main_qty","结存主数量"),c("reserved_main_qty","预留主数量"),c("frozen_main_qty","冻结主数量"),c("vendor_owned_on_hand_main_qty","供应商物权结存主数量"),c("stock_date","库存日期"));
    }

    private Dataset skuDailyDataset() {
        String[] names = {"biz_date|业务日期","warehouse_id|仓库编码","warehouse_name|仓库名称","warehouse_type|仓库类型","warehouse_role|仓库业务角色","project_no|项目号","project_name|项目名称","material_code|物料编码","material_name|物料名称","project_material_sku|项目物料 SKU","warehouse_sku_key|仓库项目物料键","material_category|物料分类","color|颜色","model|型号","uom|计量单位","packaging_level|包材层级","area_id|库区编码","area_name|库区名称","inbound_order_count|入库单数","inbound_line_count|入库行项目数","inbound_qty|入库数量","outbound_order_count|出库单数","outbound_line_count|出库行项目数","outbound_qty|出库数量","picking_task_count|拣货任务数","forklift_task_count|叉车任务数","inventory_accuracy|库存准确率%","receipt_timely_rate|入库及时率%","delivery_timely_rate|出库及时率%","avg_receipt_minutes|平均收货时长","avg_picking_minutes|平均拣货时长","exception_count|异常数","avg_outbound_lead_days|成品平均周转天数"};
        return new Dataset("运营_SKU日指标", "运营_SKU日指标", "原子事实表，粒度为业务日期 + 仓库 + 项目 + 物料。",
                "SKU_DAILY", columns(names));
    }

    private Dataset warehouseDailyDataset() {
        String[] names = {"biz_date|业务日期","warehouse_id|仓库编码","warehouse_name|仓库名称","warehouse_type|仓库类型","inbound_order_count|入库单数","outbound_order_count|出库单数","raw_inbound_ton|原材料入库量（吨）","raw_outbound_ton|原材料领用量（吨）","finished_inbound_carton|成品入库量（箱）","finished_outbound_carton|成品出库量（箱）","packaging_inbound_piece|包材入库量（个）","packaging_outbound_piece|包材领用量（个）","picking_task_count|拣货任务数","forklift_task_count|叉车任务数","inventory_accuracy|库存准确率%","receipt_timely_rate|入库及时率%","delivery_timely_rate|出库及时率%","exception_count|异常数","avg_receipt_minutes|平均收货时长","avg_picking_minutes|平均拣货时长","dock_utilization_rate|月台利用率%","overtime_hours|加班工时"};
        return new Dataset("运营_仓库每日指标", "运营_仓库每日指标", "三个仓库按日汇总的作业、时效、异常和资源指标。",
                "WAREHOUSE_DAILY", columns(names));
    }

    private Dataset areaDataset() {
        String[] names = {"snapshot_date|快照日期","warehouse_id|仓库编码","warehouse_name|仓库名称","warehouse_type|仓库类型","area_id|库区编码","area_name|库区名称","capacity_locations|容量库位数","occupied_locations|已占库位数","available_locations|可用库位数","occupancy_rate|库区占用率%","material_type_count|物料种类数","abnormal_location_count|异常库位数","frozen_qty|冻结数量","area_owner|库区负责人","status|库区状态"};
        return new Dataset("运营_库区状态", "运营_库区状态", "库区每日状态快照。", "SELECT * FROM warehouse_area_snapshot ORDER BY snapshot_date,warehouse_id,area_id", columns(names));
    }

    private Dataset exceptionDataset() {
        String[] names = {"event_id|异常编号","event_time|发生时间","event_type|异常类型","warehouse_id|仓库编码","warehouse_name|仓库名称","warehouse_type|仓库类型","project_no|项目号","project_name|项目名称","material_code|物料编码","material_name|物料名称","project_material_sku|项目物料 SKU","material_category|物料分类","color|颜色","model|型号","uom|计量单位","packaging_level|包材层级","area_id|库区编码","area_name|库区名称","severity|严重等级","handling_status|处理状态","owner|责任人","response_minutes|响应时长","sla_hours|SLA 时限","deadline_time|SLA 截止时间","close_time|关闭时间","duration_minutes|关闭耗时","is_sla_breached|是否超 SLA","root_cause|根因","action_taken|处理措施","remark|备注"};
        return new Dataset("运营_异常事件", "运营_异常事件", "异常事件全生命周期明细。", "SELECT * FROM exception_event ORDER BY event_time,event_id", columns(names));
    }

    private Dataset bomDataset() {
        String[] names = {"project_no|项目号","project_name|项目名称","finished_material_code|成品物料编码","finished_material_name|成品物料名称","finished_color|成品颜色","finished_model|成品型号","finished_uom|成品计量单位","component_category|关联物料分类","component_material_code|关联物料编码","component_material_name|关联物料名称","component_color|关联物料颜色","component_model|关联物料型号","component_uom|关联物料计量单位","component_qty_per_finished_carton|每箱成品耗用量","component_qty_uom|耗用量单位","bom_relationship|BOM 关系说明"};
        return new Dataset("项目_BOM关系", "项目_BOM关系", "项目成品 SKU 与原材料、外箱、内盒的用量关系。", "SELECT * FROM bom_relation ORDER BY project_no,finished_material_code,component_material_code", columns(names));
    }

    private Dataset targetDataset() {
        return new Dataset("运营_KPI目标", "运营_KPI目标", "KPI 目标、预警方向、计算口径与来源。", "SELECT * FROM kpi_target ORDER BY kpi_name",
                c("kpi_name","KPI 名称"),c("target_value","目标值"),c("unit","单位"),c("warning_rule","预警规则"),c("calculation_definition","计算口径"),c("data_source","数据来源"));
    }

    private Dataset inventoryAgeRuleDataset() {
        String[] names = {"rule_type","rule_name","rule_condition","result_level","action_guidance","applicable_scope"};
        return new Dataset(AGE_RULE_SHEET, AGE_RULE_SHEET, "Inventory aging and stagnation rules.",
                "AGE_RULE", technicalColumns(names));
    }

    private Dataset inventoryAgeBatchDataset() {
        String[] names = {
                "snapshot_date","age_batch_id","warehouse_id","warehouse_name","warehouse_type",
                "project_no","project_name","material_code","material_name","project_material_sku",
                "material_category","color","model","uom","batch_no","receipt_date","age_days","age_bucket",
                "batch_on_hand_qty","batch_reserved_qty","batch_frozen_qty","available_qty","unit_cost",
                "inventory_amount","last_outbound_date","days_since_last_outbound","outbound_qty_30d",
                "outbound_rate_30d","coverage_days","movement_status","stagnant_level","is_stagnant",
                "stagnant_score","priority","recommended_action","owner","data_source"
        };
        return new Dataset(AGE_BATCH_SHEET, AGE_BATCH_SHEET, "Batch-level inventory aging details.",
                "AGE_BATCH", technicalColumns(names));
    }

    private Dataset inventoryAgeSkuDataset() {
        String[] names = {
                "snapshot_date","warehouse_id","warehouse_name","warehouse_type","project_no","project_name",
                "material_code","material_name","project_material_sku","material_category","color","model","uom",
                "batch_count","on_hand_qty","available_qty","inventory_amount","weighted_avg_age_days",
                "max_age_days","dominant_age_bucket","outbound_qty_30d","outbound_rate_30d",
                "latest_sku_outbound_date","days_since_last_sku_outbound","stagnant_batch_count",
                "stagnant_inventory_amount","stagnation_ratio","stagnant_level","is_stagnant",
                "stagnant_score","priority","recommended_action","owner"
        };
        return new Dataset(AGE_SKU_SHEET, AGE_SKU_SHEET, "SKU-level inventory aging summary.",
                "AGE_SKU", technicalColumns(names));
    }

    private static Column[] technicalColumns(String[] names) {
        Column[] result = new Column[names.length];
        for (int i = 0; i < names.length; i++) result[i] = c(names[i], names[i]);
        return result;
    }

    private static Column[] columns(String[] definitions) {
        Column[] columns = new Column[definitions.length];
        for (int i = 0; i < definitions.length; i++) {
            String[] parts = definitions[i].split("\\|", 2);
            columns[i] = c(parts[0], parts[1]);
        }
        return columns;
    }

    private static Column c(String technical, String chinese) { return new Column(technical, chinese, chinese.endsWith("%")); }

    private static final class Column {
        private final String technical;
        private final String chinese;
        private final boolean percent;
        private Column(String technical, String chinese, boolean percent) { this.technical = technical; this.chinese = chinese; this.percent = percent; }
    }

    private static final class Dataset {
        private final String sheetName;
        private final String title;
        private final String description;
        private final String query;
        private final Column[] columns;
        private Dataset(String sheetName, String title, String description, String query, Column... columns) {
            this.sheetName = sheetName; this.title = title; this.description = description; this.query = query; this.columns = columns;
        }
    }

    public static class ExportFile {
        private final String filename;
        private final String contentType;
        private final byte[] content;
        public ExportFile(String filename, String contentType, byte[] content) { this.filename = filename; this.contentType = contentType; this.content = content; }
        public String getFilename() { return filename; }
        public String getContentType() { return contentType; }
        public byte[] getContent() { return content; }
    }
}
