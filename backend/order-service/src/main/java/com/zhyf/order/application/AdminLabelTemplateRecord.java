package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminLabelTemplateRecord(
        UUID id,
        UUID tenantId,
        String templateCode,
        String templateName,
        String scopeType,
        UUID institutionId,
        String institutionName,
        String prescriptionType,
        int labelWidthMm,
        int labelHeightMm,
        String contentTemplate,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
