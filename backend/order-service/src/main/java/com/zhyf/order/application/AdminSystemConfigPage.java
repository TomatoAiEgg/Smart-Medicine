package com.zhyf.order.application;

import java.util.List;

public record AdminSystemConfigPage(
        List<AdminSystemConfigRecord> records,
        long total,
        int page,
        int pageSize
) {
}
