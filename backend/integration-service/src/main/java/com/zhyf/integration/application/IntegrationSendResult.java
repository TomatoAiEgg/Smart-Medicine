package com.zhyf.integration.application;

public record IntegrationSendResult(boolean success, String responseBody) {

    public static IntegrationSendResult success(String responseBody) {
        return new IntegrationSendResult(true, responseBody);
    }

    public static IntegrationSendResult failure(String responseBody) {
        return new IntegrationSendResult(false, responseBody);
    }
}
