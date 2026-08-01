package com.zhyf.order.application;

public record AdminExportTaskFile(
        String fileName,
        String contentType,
        byte[] fileContent
) {
}
