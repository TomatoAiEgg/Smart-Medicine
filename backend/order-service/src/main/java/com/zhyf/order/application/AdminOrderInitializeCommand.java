package com.zhyf.order.application;

public record AdminOrderInitializeCommand(
        String operator,
        String reason
) {
}
