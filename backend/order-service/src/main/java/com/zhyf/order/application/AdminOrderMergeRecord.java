package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderMergeRecord(
        UUID id,
        UUID tenantId,
        String mergeNo,
        String logisticsCompany,
        String logisticsNo,
        String status,
        String remark,
        int orderCount,
        String orderNos,
        String institutionNames,
        Instant createdAt,
        Instant updatedAt
) {
}
