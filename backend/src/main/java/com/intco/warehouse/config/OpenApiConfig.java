package com.intco.warehouse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI warehouseDashboardOpenApi() {
        return new OpenAPI().info(new Info()
                .title("仓库运营看板 API")
                .description("原料库、成品库、箱盒库看板与数据交换 Controller 接口")
                .version("1.0.0"));
    }
}