package com.zhyf.order.application;

public record AdminPrescriptionActionCommand(
        String operator,
        String reason
) {
}
