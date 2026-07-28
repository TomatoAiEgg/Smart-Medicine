package com.zhyf.order.application;

import java.util.UUID;

public record AdminLogisticsAddressCostQuery(
        String keyword,
        UUID institutionId,
        String logisticsCompany,
        Boolean enabled,
        int page,
        int pageSize
) {
}
