package com.intco.warehouse.controller;

import com.intco.warehouse.service.ImportExportService;
import com.intco.warehouse.service.ImportExportService.ExportFile;
import com.intco.warehouse.service.WarehouseDataService.ImportResult;
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

@RestController
@RequestMapping("/api/data")
public class DataExchangeController {
    private final ImportExportService exchangeService;

    public DataExchangeController(ImportExportService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importFile(@RequestParam("file") MultipartFile file) throws IOException {
        return exchangeService.importDailyMetrics(file);
    }

    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> export(@RequestParam(defaultValue = "xlsx") String format) throws IOException {
        return fileResponse(exchangeService.export(format));
    }

    @GetMapping("/template")
    public ResponseEntity<ByteArrayResource> template() throws IOException {
        return fileResponse(exchangeService.template());
    }

    private ResponseEntity<ByteArrayResource> fileResponse(ExportFile file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.getContentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.getFilename(), StandardCharsets.UTF_8).build());
        headers.setContentLength(file.getContent().length);
        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(file.getContent()));
    }
}
