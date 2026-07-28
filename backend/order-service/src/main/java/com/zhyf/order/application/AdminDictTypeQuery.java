package com.zhyf.order.application;

public record AdminDictTypeQuery(
        String keyword,
        Boolean enabled,
        int page,
        int pageSize
) {
}
