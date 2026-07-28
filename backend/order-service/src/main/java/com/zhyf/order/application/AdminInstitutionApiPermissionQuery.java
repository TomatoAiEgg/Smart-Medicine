package com.zhyf.order.application;

import java.util.UUID;

public record AdminInstitutionApiPermissionQuery(
        String keyword,
        UUID institutionId,
        UUID apiId,
        Boolean enabled,
        int page,
        int pageSize
) {
}
