package com.zhyf.callback.application;

public record CallbackSendResult(boolean success, String responseBody) {

    public static CallbackSendResult success(String responseBody) {
        return new CallbackSendResult(true, responseBody);
    }

    public static CallbackSendResult failure(String responseBody) {
        return new CallbackSendResult(false, responseBody);
    }
}
