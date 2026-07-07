package com.zhyf.logistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zhyf.logistics")
public class LogisticsProperties {

    private String orderServiceBaseUrl = "http://localhost:18082";
    private String callbackServiceBaseUrl = "http://localhost:18089";

    public String getOrderServiceBaseUrl() {
        return orderServiceBaseUrl;
    }

    public void setOrderServiceBaseUrl(String orderServiceBaseUrl) {
        this.orderServiceBaseUrl = orderServiceBaseUrl;
    }

    public String getCallbackServiceBaseUrl() {
        return callbackServiceBaseUrl;
    }

    public void setCallbackServiceBaseUrl(String callbackServiceBaseUrl) {
        this.callbackServiceBaseUrl = callbackServiceBaseUrl;
    }
}
