package com.intco.warehouse.controller;

import com.intco.warehouse.service.ImportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.intco.warehouse.service.ImportExportService.ExportFile;
import com.intco.warehouse.service.WarehouseImportService.ImportSummary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "数据交换", description = "数据库数据导入、导出、模板与状态接口")
@RestController
@RequestMapping("/api/data")
public class DataExchangeController {
    private final ImportExportService exchangeService;

    public DataExchangeController(ImportExportService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @Operation(summary = "导入仓库数据文件")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportSummary importFile(@RequestParam("file") MultipartFile file) throws IOException {
        return exchangeService.importFile(file);
    }

    @Operation(summary = "导出数据库数据")
    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> export(@RequestParam(defaultValue = "xlsx") String format) throws IOException {
        return fileResponse(exchangeService.export(format));
    }

    @Operation(summary = "下载数据导入模板")
    @GetMapping("/template")
    public ResponseEntity<ByteArrayResource> template() throws IOException {
        return fileResponse(exchangeService.template());
    }

    @Operation(summary = "查询数据库数据状态")
    @GetMapping("/status")
    public java.util.Map<String, Object> status() {
        return exchangeService.status();
    }

    private ResponseEntity<ByteArrayResource> fileResponse(ExportFile file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.getContentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.getFilename(), StandardCharsets.UTF_8).build());
        headers.setContentLength(file.getContent().length);
        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(file.getContent()));
    }
}
