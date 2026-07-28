package com.zhyf.order.application;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminLogisticsSpecialRuleCommand(
        UUID institutionId,
        String ruleName,
        String logisticsCompany,
        BigDecimal baseFee,
        BigDecimal extraFee,
        BigDecimal freeThreshold,
        String remark,
        Boolean enabled
) {
}
