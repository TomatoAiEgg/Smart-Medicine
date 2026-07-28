package com.zhyf.order.application;

import java.util.UUID;

public record AdminLabelTemplateCommand(
        String templateCode,
        String templateName,
        String scopeType,
        UUID institutionId,
        String prescriptionType,
        Integer labelWidthMm,
        Integer labelHeightMm,
        String contentTemplate,
        Boolean enabled
) {
}
