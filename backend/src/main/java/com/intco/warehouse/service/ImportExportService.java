package com.intco.warehouse.service;

import com.intco.warehouse.model.DashboardData;
import com.intco.warehouse.model.DashboardData.Alert;
import com.intco.warehouse.model.DashboardData.DailyMetric;
import com.intco.warehouse.model.DashboardData.Forklift;
import com.intco.warehouse.model.DashboardData.Target;
import com.intco.warehouse.model.DashboardData.Zone;
import com.intco.warehouse.service.WarehouseDataService.ImportResult;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportExportService {
    private static final String[] DAILY_HEADERS = {"日期", "入库箱数", "出库箱数", "拣货任务", "叉车任务", "库存准确率", "入库及时率", "出库及时率", "异常数", "收货时长(分钟)", "拣货时长(分钟)", "平均作业时长(分钟)", "月台利用率", "加班工时"};
    private static final Map<String, String> HEADER_ALIASES = buildHeaderAliases();
    private final WarehouseDataService dataService;

    public ImportExportService(WarehouseDataService dataService) {
        this.dataService = dataService;
    }

    public ImportResult importDailyMetrics(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<DailyMetric> rows;
        if (filename.endsWith(".xlsx")) {
            rows = readWorkbook(file);
        } else if (filename.endsWith(".csv")) {
            rows = readCsv(file);
        } else {
            throw new IllegalArgumentException("仅支持 .xlsx 或 .csv 文件");
        }
        return dataService.mergeDailyMetrics(rows);
    }

    public ExportFile export(String format) throws IOException {
        if ("csv".equalsIgnoreCase(format)) {
            return new ExportFile("warehouse-daily-metrics.csv", "text/csv;charset=UTF-8", exportCsv());
        }
        if (!"xlsx".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException("导出格式仅支持 xlsx 或 csv");
        }
        return new ExportFile("warehouse-operation-report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", exportWorkbook());
    }

    public ExportFile template() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("日指标");
            CellStyle header = headerStyle(workbook);
            writeHeader(sheet, DAILY_HEADERS, header);
            Object[] example = {"2026-08-01", 520, 488, 126, 245, .986, .958, .947, 4, 41, 38, 38, .72, 1.5};
            writeRow(sheet.createRow(1), example);
            Row note = sheet.createRow(3);
            note.createCell(0).setCellValue("说明：日期为必填项；百分比可填写 0.986 或 98.6%；同日期数据会更新，新增日期会追加。");
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(3, 3, 0, DAILY_HEADERS.length - 1));
            sizeColumns(sheet, DAILY_HEADERS.length);
            workbook.write(output);
            return new ExportFile("warehouse-import-template.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private List<DailyMetric> readWorkbook(MultipartFile file) throws IOException {
        List<DailyMetric> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("日指标");
            if (sheet == null) sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() < 2) throw new IllegalArgumentException("Excel 中没有可导入的数据行");
            Map<Integer, String> columns = new HashMap<>();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            for (Cell cell : header) {
                String canonical = canonicalHeader(cell.getStringCellValue());
                if (canonical != null) columns.put(cell.getColumnIndex(), canonical);
            }
            requireDateHeader(columns.values());
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            for (int rowIndex = header.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || rowIsBlank(row, formatter)) continue;
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<Integer, String> entry : columns.entrySet()) {
                    Cell cell = row.getCell(entry.getKey());
                    values.put(entry.getValue(), cellValue(cell, formatter));
                }
                rows.add(toDailyMetric(values, rowIndex + 1));
                if (rows.size() > 10000) throw new IllegalArgumentException("单次导入最多支持 10,000 行");
            }
        }
        return rows;
    }

    private List<DailyMetric> readCsv(MultipartFile file) throws IOException {
        List<DailyMetric> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).build().parse(reader)) {
            Map<String, String> sourceToCanonical = new LinkedHashMap<>();
            for (String source : parser.getHeaderMap().keySet()) {
                String canonical = canonicalHeader(source);
                if (canonical != null) sourceToCanonical.put(source, canonical);
            }
            requireDateHeader(sourceToCanonical.values());
            for (CSVRecord record : parser) {
                Map<String, String> values = new HashMap<>();
                sourceToCanonical.forEach((source, canonical) -> values.put(canonical, record.get(source)));
                rows.add(toDailyMetric(values, (int) record.getRecordNumber() + 1));
                if (rows.size() > 10000) throw new IllegalArgumentException("单次导入最多支持 10,000 行");
            }
        }
        return rows;
    }

    private DailyMetric toDailyMetric(Map<String, String> values, int rowNumber) {
        try {
            DailyMetric row = new DailyMetric();
            row.setDate(parseDate(values.get("date")));
            row.setInbound(integer(values.get("inbound"), "入库箱数"));
            row.setOutbound(integer(values.get("outbound"), "出库箱数"));
            row.setPicking(integer(values.get("picking"), "拣货任务"));
            row.setForkliftTasks(integer(values.get("forkliftTasks"), "叉车任务"));
            row.setInventoryAccuracy(rate(values.get("inventoryAccuracy"), "库存准确率"));
            row.setReceivingTimely(rate(values.get("receivingTimely"), "入库及时率"));
            row.setDeliveryTimely(rate(values.get("deliveryTimely"), "出库及时率"));
            row.setExceptions(integer(values.get("exceptions"), "异常数"));
            row.setReceiptMinutes(number(values.get("receiptMinutes"), "收货时长"));
            row.setPickingMinutes(number(values.get("pickingMinutes"), "拣货时长"));
            row.setAverageDuration(number(values.get("averageDuration"), "平均作业时长"));
            row.setDockUtilization(rate(values.get("dockUtilization"), "月台利用率"));
            row.setOvertimeHours(number(values.get("overtimeHours"), "加班工时"));
            return row;
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("第 " + rowNumber + " 行：" + error.getMessage());
        }
    }

    private byte[] exportCsv() throws IOException {
        DashboardData data = dataService.currentData();
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(String.join(",", DAILY_HEADERS)).append("\r\n");
        for (DailyMetric row : data.getDaily()) {
            csv.append(row.getDate()).append(',').append(row.getInbound()).append(',').append(row.getOutbound()).append(',')
                    .append(row.getPicking()).append(',').append(row.getForkliftTasks()).append(',')
                    .append(row.getInventoryAccuracy()).append(',').append(row.getReceivingTimely()).append(',')
                    .append(row.getDeliveryTimely()).append(',').append(row.getExceptions()).append(',')
                    .append(row.getReceiptMinutes()).append(',').append(row.getPickingMinutes()).append(',')
                    .append(row.getAverageDuration()).append(',').append(row.getDockUtilization()).append(',')
                    .append(row.getOvertimeHours()).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportWorkbook() throws IOException {
        DashboardData data = dataService.currentData();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);
            CellStyle percent = workbook.createCellStyle();
            percent.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));

            Sheet daily = workbook.createSheet("日指标");
            writeHeader(daily, DAILY_HEADERS, header);
            int rowIndex = 1;
            for (DailyMetric item : data.getDaily()) {
                Row row = daily.createRow(rowIndex++);
                writeRow(row, new Object[]{item.getDate(), item.getInbound(), item.getOutbound(), item.getPicking(), item.getForkliftTasks(), item.getInventoryAccuracy(), item.getReceivingTimely(), item.getDeliveryTimely(), item.getExceptions(), item.getReceiptMinutes(), item.getPickingMinutes(), item.getAverageDuration(), item.getDockUtilization(), item.getOvertimeHours()});
                for (int column : Arrays.asList(5, 6, 7, 12)) row.getCell(column).setCellStyle(percent);
            }
            sizeColumns(daily, DAILY_HEADERS.length);

            Sheet zones = workbook.createSheet("库区状态");
            String[] zoneHeaders = {"快照日期", "仓库", "库区编码", "库区名称", "总库位", "已用库位", "可用库位", "占用率", "物料种类", "异常库位", "冻结库位", "负责人", "状态"};
            writeHeader(zones, zoneHeaders, header); rowIndex = 1;
            for (Zone item : data.getZones()) {
                Row row = zones.createRow(rowIndex++);
                writeRow(row, new Object[]{item.getSnapshotDate(), item.getWarehouse(), item.getCode(), item.getName(), item.getCapacity(), item.getOccupied(), item.getAvailable(), item.getOccupancy(), item.getMaterialTypes(), item.getAbnormal(), item.getFrozen(), item.getManager(), item.getStatus()});
                row.getCell(7).setCellStyle(percent);
            }
            sizeColumns(zones, zoneHeaders.length);

            Sheet alerts = workbook.createSheet("异常事件");
            String[] alertHeaders = {"事件编号", "日期", "时间", "类型", "标题", "仓库", "库区", "等级", "状态", "责任人", "响应分钟", "SLA小时", "持续小时", "是否超时", "原因", "建议", "物料", "项目"};
            writeHeader(alerts, alertHeaders, header); rowIndex = 1;
            for (Alert item : data.getAlerts()) writeRow(alerts.createRow(rowIndex++), new Object[]{item.getId(), item.getDate(), item.getTime(), item.getType(), item.getTitle(), item.getWarehouse(), item.getZone(), item.getSeverity(), item.getStatus(), item.getOwner(), item.getResponseMinutes(), item.getSlaHours(), item.getDurationHours(), item.isSlaBreached() ? "是" : "否", item.getDescription(), item.getRecommendation(), item.getMaterial(), item.getProject()});
            sizeColumns(alerts, alertHeaders.length);

            Sheet targets = workbook.createSheet("目标阈值");
            String[] targetHeaders = {"指标编码", "指标名称", "目标", "单位", "预警规则", "指标定义", "数据来源"};
            writeHeader(targets, targetHeaders, header); rowIndex = 1;
            for (Target item : data.getTargets()) writeRow(targets.createRow(rowIndex++), new Object[]{item.getKey(), item.getName(), item.getTarget(), item.getUnit(), item.getRule(), item.getDefinition(), item.getSource()});
            sizeColumns(targets, targetHeaders.length);

            Sheet resources = workbook.createSheet("叉车资源");
            String[] resourceHeaders = {"任务池", "仓库", "状态", "任务说明", "当前任务", "峰值任务", "负荷"};
            writeHeader(resources, resourceHeaders, header); rowIndex = 1;
            for (Forklift item : data.getForklifts()) {
                Row row = resources.createRow(rowIndex++);
                writeRow(row, new Object[]{item.getId(), item.getZone(), item.getStatus(), item.getTask(), item.getTasks(), item.getPeakTasks(), item.getLoad()});
                row.getCell(6).setCellStyle(percent);
            }
            sizeColumns(resources, resourceHeaders.length);

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void writeHeader(Sheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));
    }

    private static void writeRow(Row row, Object[] values) {
        for (int index = 0; index < values.length; index++) {
            Cell cell = row.createCell(index);
            Object value = values[index];
            if (value instanceof Number) cell.setCellValue(((Number) value).doubleValue());
            else if (value instanceof Boolean) cell.setCellValue((Boolean) value);
            else cell.setCellValue(value == null ? "" : String.valueOf(value));
        }
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private static void sizeColumns(Sheet sheet, int count) {
        for (int column = 0; column < count; column++) {
            sheet.autoSizeColumn(column);
            sheet.setColumnWidth(column, Math.min(60 * 256, Math.max(12 * 256, sheet.getColumnWidth(column) + 768)));
        }
    }

    private static String cellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return formatter.formatCellValue(cell).trim();
    }

    private static boolean rowIsBlank(Row row, DataFormatter formatter) {
        for (Cell cell : row) if (!formatter.formatCellValue(cell).trim().isEmpty()) return false;
        return true;
    }

    private static void requireDateHeader(Iterable<String> values) {
        for (String value : values) if ("date".equals(value)) return;
        throw new IllegalArgumentException("缺少必填列“日期”");
    }

    private static String canonicalHeader(String source) {
        if (source == null) return null;
        return HEADER_ALIASES.get(source.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT).replace(" ", ""));
    }

    private static Map<String, String> buildHeaderAliases() {
        Map<String, String> map = new HashMap<>();
        alias(map, "date", "日期", "date", "业务日期");
        alias(map, "inbound", "入库箱数", "入库", "inbound");
        alias(map, "outbound", "出库箱数", "出库", "outbound");
        alias(map, "picking", "拣货任务", "拣货", "picking");
        alias(map, "forkliftTasks", "叉车任务", "forklifttasks", "叉车任务数");
        alias(map, "inventoryAccuracy", "库存准确率", "inventoryaccuracy");
        alias(map, "receivingTimely", "入库及时率", "receivingtimely");
        alias(map, "deliveryTimely", "出库及时率", "deliverytimely");
        alias(map, "exceptions", "异常数", "exceptions", "异常事件数");
        alias(map, "receiptMinutes", "收货时长(分钟)", "收货时长（分钟）", "receiptminutes", "收货时长");
        alias(map, "pickingMinutes", "拣货时长(分钟)", "拣货时长（分钟）", "pickingminutes", "拣货时长");
        alias(map, "averageDuration", "平均作业时长(分钟)", "平均作业时长（分钟）", "averageduration", "平均作业时长");
        alias(map, "dockUtilization", "月台利用率", "dockutilization");
        alias(map, "overtimeHours", "加班工时", "overtimehours");
        return map;
    }

    private static void alias(Map<String, String> map, String canonical, String... aliases) {
        for (String value : aliases) map.put(value.toLowerCase(Locale.ROOT).replace(" ", ""), canonical);
    }

    private static String parseDate(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("日期不能为空");
        List<DateTimeFormatter> formats = Arrays.asList(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("yyyy/M/d"), DateTimeFormatter.ofPattern("M/d/yyyy"), DateTimeFormatter.ofPattern("M/d/yy"));
        for (DateTimeFormatter format : formats) {
            try { return LocalDate.parse(value.trim(), format).toString(); } catch (DateTimeParseException ignored) { }
        }
        throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd");
    }

    private static int integer(String value, String field) { return (int) Math.round(number(value, field)); }

    private static double number(String value, String field) {
        if (value == null || value.trim().isEmpty()) return 0;
        try { return Double.parseDouble(value.trim().replace(",", "")); }
        catch (NumberFormatException error) { throw new IllegalArgumentException(field + "不是有效数字"); }
    }

    private static double rate(String value, String field) {
        if (value == null || value.trim().isEmpty()) return 0;
        boolean percent = value.contains("%");
        double parsed = number(value.replace("%", ""), field);
        if (percent || parsed > 1.5) parsed /= 100;
        if (parsed < 0 || parsed > 1) throw new IllegalArgumentException(field + "应在 0% 至 100% 之间");
        return parsed;
    }

    public static class ExportFile {
        private final String filename;
        private final String contentType;
        private final byte[] content;
        public ExportFile(String filename, String contentType, byte[] content) {
            this.filename = filename;
            this.contentType = contentType;
            this.content = content;
        }
        public String getFilename() { return filename; }
        public String getContentType() { return contentType; }
        public byte[] getContent() { return content; }
        public ByteArrayInputStream inputStream() { return new ByteArrayInputStream(content); }
    }
}
