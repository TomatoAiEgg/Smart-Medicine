package com.zhyf.order.application;

import java.util.List;

public record AdminOrderInterceptRulePage(
        List<AdminOrderInterceptRuleRecord> records,
        long total,
        int page,
        int pageSize
) {
}
