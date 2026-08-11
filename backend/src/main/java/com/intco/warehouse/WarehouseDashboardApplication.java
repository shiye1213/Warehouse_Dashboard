package com.intco.warehouse;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.intco.warehouse.mapper")
@EnableScheduling
public class WarehouseDashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(WarehouseDashboardApplication.class, args);
    }
}
