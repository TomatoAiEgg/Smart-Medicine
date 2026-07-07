package com.zhyf.gateway.app;

import java.util.List;
import java.util.UUID;

public record InstitutionAppView(
        UUID tenantId,
        UUID institutionId,
        String appKey,
        String appSecret,
        String signType,
        String callbackUrl,
        boolean enabled,
        List<String> ipWhitelist
) {
}
