package com.zhyf.order.application;

import java.util.List;

public record AdminOrderReceiptPage(
        List<AdminOrderReceiptItem> records,
        long total,
        int page,
        int pageSize
) {
}
