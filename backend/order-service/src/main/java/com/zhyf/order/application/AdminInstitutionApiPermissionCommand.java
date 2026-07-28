package com.zhyf.order.application;

import java.util.UUID;

public record AdminInstitutionApiPermissionCommand(
        UUID institutionId,
        UUID apiId,
        String remark,
        Boolean enabled
) {
}
