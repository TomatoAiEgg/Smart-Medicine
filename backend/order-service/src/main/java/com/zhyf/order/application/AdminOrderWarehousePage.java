package com.zhyf.order.application;

import java.util.List;

public record AdminOrderWarehousePage(
        List<AdminOrderWarehouseItem> records,
        long total,
        int page,
        int pageSize
) {
}
