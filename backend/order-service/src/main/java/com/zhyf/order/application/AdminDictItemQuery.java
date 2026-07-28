package com.zhyf.order.application;

import java.util.UUID;

public record AdminDictItemQuery(
        String keyword,
        UUID typeId,
        Boolean enabled,
        int page,
        int pageSize
) {
}
