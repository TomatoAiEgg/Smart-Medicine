package com.zhyf.order.application;

public record AdminHerbQuery(
        String keyword,
        Boolean enabled,
        int page,
        int pageSize
) {
}
