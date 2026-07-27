package com.zhyf.order.application;

import java.util.List;

public record AdminManualProcessPage(
        List<AdminManualProcessItem> records,
        long total,
        int page,
        int pageSize
) {
}
