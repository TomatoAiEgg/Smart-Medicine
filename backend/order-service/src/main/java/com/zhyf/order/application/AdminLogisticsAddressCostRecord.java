package com.zhyf.order.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminLogisticsAddressCostRecord(
        UUID id,
        UUID tenantId,
        UUID institutionId,
        String institutionCode,
        String institutionName,
        String institutionType,
        String logisticsCompany,
        String province,
        String city,
        String district,
        BigDecimal costAmount,
        String remark,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
