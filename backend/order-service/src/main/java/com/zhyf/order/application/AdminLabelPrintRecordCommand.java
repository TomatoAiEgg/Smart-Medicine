package com.zhyf.order.application;

import java.util.UUID;

public record AdminLabelPrintRecordCommand(
        String printStatus,
        UUID templateId,
        String templateName,
        String failureReason,
        String operator,
        UUID retryOf
) {
}
