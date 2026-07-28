package com.zhyf.order.application;

import java.util.UUID;

public record AdminLabelTemplateQuery(
        String keyword,
        UUID institutionId,
        String prescriptionType,
        Boolean enabled,
        int page,
        int pageSize
) {
}
