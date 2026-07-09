package com.zhyf.integration.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IntegrationDispatchJob {

    private final IntegrationService integrationService;
    private final IntegrationProperties properties;

    public IntegrationDispatchJob(IntegrationService integrationService, IntegrationProperties properties) {
        this.integrationService = integrationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${zhyf.integration.dispatch-fixed-delay-ms:5000}")
    public void dispatch() {
        if (!properties.isDispatchEnabled()) {
            return;
        }
        integrationService.dispatchDueRetryTasks(properties.getDispatchBatchSize());
    }
}
