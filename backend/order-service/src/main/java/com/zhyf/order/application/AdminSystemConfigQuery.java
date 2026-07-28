package com.zhyf.order.application;

public record AdminSystemConfigQuery(
        String keyword,
        String valueType,
        Boolean enabled,
        int page,
        int pageSize
) {
}
