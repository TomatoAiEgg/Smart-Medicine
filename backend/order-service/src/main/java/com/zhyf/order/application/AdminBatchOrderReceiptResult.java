package com.zhyf.order.application;

import java.util.List;

public record AdminBatchOrderReceiptResult(
        int totalCount,
        int successCount,
        int failCount,
        List<AdminOrderReceiptResult> items
) {
}
