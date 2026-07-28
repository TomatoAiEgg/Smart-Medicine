package com.zhyf.order.application;

import java.util.List;

public record AdminHerbIndexOperationLogPage(
        List<AdminHerbIndexOperationLogRecord> records,
        long total,
        int page,
        int pageSize
) {
}
