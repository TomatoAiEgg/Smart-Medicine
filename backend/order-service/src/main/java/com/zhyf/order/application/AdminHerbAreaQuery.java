package com.zhyf.order.application;

public record AdminHerbAreaQuery(
        String keyword,
        Boolean enabled,
        int page,
        int pageSize
) {
}
