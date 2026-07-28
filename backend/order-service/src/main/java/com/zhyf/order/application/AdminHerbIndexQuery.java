package com.zhyf.order.application;

import java.util.UUID;

public record AdminHerbIndexQuery(
        String keyword,
        UUID institutionId,
        Boolean enabled,
        int page,
        int pageSize
) {
}
