package com.zhyf.order.application;

public record OrderStatusUpdateCommand(
        String targetStatus,
        String operatorType,
        String source
) {
}
