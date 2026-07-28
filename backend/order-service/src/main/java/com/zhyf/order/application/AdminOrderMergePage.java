package com.zhyf.order.application;

import java.util.List;

public record AdminOrderMergePage(
        List<AdminOrderMergeRecord> records,
        long total,
        int page,
        int pageSize
) {
}
