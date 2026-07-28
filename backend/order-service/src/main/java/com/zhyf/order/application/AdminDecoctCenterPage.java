package com.zhyf.order.application;

import java.util.List;

public record AdminDecoctCenterPage(
        List<AdminDecoctCenterRecord> records,
        long total,
        int page,
        int pageSize
) {
}
