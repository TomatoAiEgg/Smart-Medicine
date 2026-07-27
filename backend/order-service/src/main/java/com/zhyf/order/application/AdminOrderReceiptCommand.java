package com.zhyf.order.application;

public record AdminOrderReceiptCommand(
        String operator,
        String reason
) {
}
