package com.intco.warehouse;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.intco.warehouse.mapper")
public class WarehouseDashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(WarehouseDashboardApplication.class, args);
    }
}
