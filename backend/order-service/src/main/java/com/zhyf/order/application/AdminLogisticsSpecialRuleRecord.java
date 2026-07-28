package com.zhyf.order.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminLogisticsSpecialRuleRecord(
        UUID id,
        UUID tenantId,
        UUID institutionId,
        String institutionCode,
        String institutionName,
        String institutionType,
        String ruleName,
        String logisticsCompany,
        BigDecimal baseFee,
        BigDecimal extraFee,
        BigDecimal freeThreshold,
        String remark,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
