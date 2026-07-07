package com.zhyf.order.domain;

import java.util.UUID;

public record InstitutionApp(
        UUID tenantId,
        UUID institutionId,
        String appKey,
        String appSecret,
        String callbackUrl
) {
}
