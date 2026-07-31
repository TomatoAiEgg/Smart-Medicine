package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record LegacyPdaLabelPrintResult(
        UUID printRecordId,
        String recipeId,
        Integer printNum,
        String dmjCode,
        String dmjIp,
        String printStatus,
        String message,
        Instant createdAt
) {
}
