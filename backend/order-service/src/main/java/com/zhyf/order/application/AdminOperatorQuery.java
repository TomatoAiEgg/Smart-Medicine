package com.zhyf.order.application;

public record AdminOperatorQuery(
        String keyword,
        Boolean enabled,
        int page,
        int pageSize
) {
}
