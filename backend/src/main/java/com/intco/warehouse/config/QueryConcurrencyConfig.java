package com.intco.warehouse.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class QueryConcurrencyConfig {
    @Bean(name = "warehouseQueryExecutor")
    public ThreadPoolTaskExecutor warehouseQueryExecutor(
            @Value("${warehouse.query-concurrency:6}") int concurrency,
            @Value("${warehouse.query-queue-capacity:100}") int queueCapacity) {
        int poolSize = Math.max(1, concurrency);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setThreadNamePrefix("warehouse-query-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
