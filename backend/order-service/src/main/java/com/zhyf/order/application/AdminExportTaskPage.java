package com.zhyf.order.application;

import java.util.List;

public record AdminExportTaskPage(
        List<AdminExportTaskRecord> records,
        long total,
        int page,
        int pageSize
) {
}
