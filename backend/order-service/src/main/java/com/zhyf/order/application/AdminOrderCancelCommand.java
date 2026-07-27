package com.zhyf.order.application;

public record AdminOrderCancelCommand(
        String operator,
        String reason
) {
}
