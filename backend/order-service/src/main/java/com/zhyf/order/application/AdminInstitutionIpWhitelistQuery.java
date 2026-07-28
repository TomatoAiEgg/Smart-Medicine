package com.zhyf.order.application;

import java.util.UUID;

public record AdminInstitutionIpWhitelistQuery(
        String keyword,
        UUID institutionId,
        String ipRange,
        Boolean enabled,
        int page,
        int pageSize
) {
}
