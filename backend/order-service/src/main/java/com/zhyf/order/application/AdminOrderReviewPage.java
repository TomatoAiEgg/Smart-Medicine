package com.zhyf.order.application;

import java.util.List;

public record AdminOrderReviewPage(
        List<AdminOrderReviewItem> records,
        long total,
        int page,
        int pageSize
) {
}
