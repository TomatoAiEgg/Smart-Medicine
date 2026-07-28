package com.zhyf.order.application;

public record AdminOperatorQuery(
        String keyword,
        String roleCode,
        Boolean enabled,
        int page,
        int pageSize
) {
}
