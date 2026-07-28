package com.zhyf.order.application;

public record AdminDecoctCenterQuery(
        String keyword,
        Boolean enabled,
        int page,
        int pageSize
) {
}
