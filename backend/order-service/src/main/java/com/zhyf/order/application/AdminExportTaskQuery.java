package com.zhyf.order.application;

public record AdminExportTaskQuery(
        String taskType,
        String taskStatus,
        String keyword,
        int page,
        int pageSize
) {
}
