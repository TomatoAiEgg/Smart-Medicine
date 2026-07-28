package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminDictItemRecord(
        UUID id,
        UUID tenantId,
        UUID typeId,
        String typeCode,
        String typeName,
        String itemCode,
        String itemName,
        String itemValue,
        int sortNo,
        boolean enabled,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
}
