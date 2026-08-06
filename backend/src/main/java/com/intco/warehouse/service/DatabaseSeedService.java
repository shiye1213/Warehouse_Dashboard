package com.intco.warehouse.service;

import java.io.IOException;
import java.io.InputStream;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class DatabaseSeedService {
    private final WarehouseImportService importService;
    private final boolean seedEnabled;

    public DatabaseSeedService(WarehouseImportService importService,
                               @Value("${warehouse.seed-enabled:true}") boolean seedEnabled) {
        this.importService = importService;
        this.seedEnabled = seedEnabled;
    }

    @PostConstruct
    public void seedWhenEmpty() throws IOException {
        if (!seedEnabled || !importService.isEmpty()) return;
        ClassPathResource resource = new ClassPathResource("data/warehouse-data.xlsx");
        try (InputStream input = resource.getInputStream()) {
            importService.importWorkbook(input, "classpath:data/warehouse-data.xlsx");
        }
    }
}
