package com.zhyf.order.application;

public record AdminOrderInterceptRuleQuery(
        String keyword,
        String interceptStage,
        Boolean enabled,
        int page,
        int pageSize
) {
}
