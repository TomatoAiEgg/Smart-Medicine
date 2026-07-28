package com.zhyf.order.application;

import java.util.UUID;

public record AdminInstitutionIpWhitelistCommand(
        UUID institutionId,
        String ipRange,
        Boolean enabled
) {
}
