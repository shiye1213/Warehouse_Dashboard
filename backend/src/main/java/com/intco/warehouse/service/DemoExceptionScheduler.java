package com.intco.warehouse.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "warehouse.demo-mutation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoExceptionScheduler {
    private final DemoExceptionMutationService mutationService;

    public DemoExceptionScheduler(DemoExceptionMutationService mutationService) {
        this.mutationService = mutationService;
    }

    @Scheduled(
            fixedDelayString = "${warehouse.demo-mutation.interval-ms:300000}",
            initialDelayString = "${warehouse.demo-mutation.initial-delay-ms:300000}")
    public void mutateDemoException() {
        mutationService.toggle();
    }
}
