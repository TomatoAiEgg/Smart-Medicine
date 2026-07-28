package com.zhyf.order.application;

import java.util.List;

public record AdminOperatorPage(
        List<AdminOperatorRecord> records,
        long total,
        int page,
        int pageSize
) {
}
