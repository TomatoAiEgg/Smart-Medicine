package com.zhyf.order.application;

import java.util.UUID;

public record AdminHerbIndexOperationLogQuery(
        String keyword,
        UUID institutionId,
        String actionType,
        int page,
        int pageSize
) {
}
