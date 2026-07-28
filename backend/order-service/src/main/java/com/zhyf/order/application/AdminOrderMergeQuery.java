package com.zhyf.order.application;

public record AdminOrderMergeQuery(
        String keyword,
        String status,
        int page,
        int pageSize
) {
}
