package com.zhyf.order.application;

import java.util.List;

public record AdminLogisticsAddressCostPage(
        List<AdminLogisticsAddressCostRecord> records,
        long total,
        int page,
        int pageSize
) {
}
