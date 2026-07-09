package com.zhyf.integration.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zhyf.integration")
public class IntegrationProperties {

    private boolean dispatchEnabled;
    private int dispatchBatchSize = 20;
    private int maxRetries = 5;
    private long initialRetryDelaySeconds = 60;
    private long maxRetryDelaySeconds = 1800;

    public boolean isDispatchEnabled() {
        return dispatchEnabled;
    }

    public void setDispatchEnabled(boolean dispatchEnabled) {
        this.dispatchEnabled = dispatchEnabled;
    }

    public int getDispatchBatchSize() {
        return dispatchBatchSize;
    }

    public void setDispatchBatchSize(int dispatchBatchSize) {
        this.dispatchBatchSize = Math.max(1, dispatchBatchSize);
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(1, maxRetries);
    }

    public long getInitialRetryDelaySeconds() {
        return initialRetryDelaySeconds;
    }

    public void setInitialRetryDelaySeconds(long initialRetryDelaySeconds) {
        this.initialRetryDelaySeconds = Math.max(1, initialRetryDelaySeconds);
    }

    public long getMaxRetryDelaySeconds() {
        return maxRetryDelaySeconds;
    }

    public void setMaxRetryDelaySeconds(long maxRetryDelaySeconds) {
        this.maxRetryDelaySeconds = Math.max(1, maxRetryDelaySeconds);
    }
}
