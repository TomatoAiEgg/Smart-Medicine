package com.zhyf.order.application;

import java.util.UUID;

public record AdminLabelPrintRecordCommand(
        String printStatus,
        String printChannel,
        String printerCode,
        String printerName,
        String provider,
        String providerTaskNo,
        UUID templateId,
        String templateName,
        String requestParam,
        String responseBody,
        String failureReason,
        String operator,
        UUID retryOf
) {
}
