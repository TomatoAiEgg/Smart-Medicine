package com.zhyf.order.application;

import java.util.List;

public record AdminOrderRecheckPage(
        List<AdminOrderRecheckItem> records,
        long total,
        int page,
        int pageSize
) {
}
