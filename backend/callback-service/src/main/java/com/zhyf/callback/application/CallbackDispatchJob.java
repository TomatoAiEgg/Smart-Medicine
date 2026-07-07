package com.zhyf.callback.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CallbackDispatchJob {

    private final CallbackService callbackService;
    private final CallbackProperties properties;

    public CallbackDispatchJob(CallbackService callbackService, CallbackProperties properties) {
        this.callbackService = callbackService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${zhyf.callback.dispatch-fixed-delay-ms:5000}")
    public void dispatch() {
        if (!properties.isDispatchEnabled()) {
            return;
        }
        callbackService.dispatchDueCallbacks(properties.getDispatchBatchSize());
    }
}
