package com.zhyf.order.application;

public record AdminOperatorRoleQuery(
        String keyword,
        int page,
        int pageSize
) {
}
