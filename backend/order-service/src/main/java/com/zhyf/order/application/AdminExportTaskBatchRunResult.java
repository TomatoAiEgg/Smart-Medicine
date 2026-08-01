package com.zhyf.order.application;

import java.util.List;

public record AdminExportTaskBatchRunResult(
        int totalCount,
        int successCount,
        int failCount,
        List<AdminExportTaskRecord> records
) {
}
