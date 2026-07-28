package com.zhyf.order.application;

public record AdminOperatorCommand(
        String username,
        String displayName,
        String roleCode,
        Boolean enabled
) {
}
