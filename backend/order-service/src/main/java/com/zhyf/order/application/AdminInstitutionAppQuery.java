package com.zhyf.order.application;

import java.util.UUID;

public record AdminInstitutionAppQuery(
        String keyword,
        UUID institutionId,
        Boolean enabled,
        int page,
        int pageSize
) {
}
