package com.zhyf.order.application;

import java.util.List;

public record AdminLogisticsSpecialRulePage(
        List<AdminLogisticsSpecialRuleRecord> records,
        long total,
        int page,
        int pageSize
) {
}
