package com.zhyf.order.application;

import java.util.UUID;

public record AdminInstitutionAppCommand(
        UUID institutionId,
        String appKey,
        String appSecret,
        String signType,
        String callbackUrl,
        Boolean enabled
) {
}
