package com.zhyf.order.application;

import java.util.List;

public record AdminOrderPage(
        List<AdminOrderListItem> records,
        long total,
        int page,
        int pageSize
) {
}
