package com.zhyf.order.application;

import java.util.UUID;

public record AdminHerbIndexCommand(
        UUID institutionId,
        String externalHerbCode,
        String externalHerbName,
        UUID herbId,
        String matchType,
        Boolean enabled,
        String remark
) {
}
