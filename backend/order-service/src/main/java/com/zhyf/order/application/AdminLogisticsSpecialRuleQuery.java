package com.zhyf.order.application;

import java.util.UUID;

public record AdminLogisticsSpecialRuleQuery(
        String keyword,
        UUID institutionId,
        Boolean enabled,
        int page,
        int pageSize
) {
}
