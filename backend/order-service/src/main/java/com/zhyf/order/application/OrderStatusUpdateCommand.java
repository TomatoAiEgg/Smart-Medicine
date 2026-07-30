package com.zhyf.order.application;

public record OrderStatusUpdateCommand(
        String targetStatus,
        String operatorType,
        String source,
        String batchNo
) {
    public OrderStatusUpdateCommand(String targetStatus, String operatorType, String source) {
        this(targetStatus, operatorType, source, null);
    }
}
