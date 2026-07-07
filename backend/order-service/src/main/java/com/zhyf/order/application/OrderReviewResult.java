package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record OrderReviewResult(
        UUID taskId,
        UUID orderId,
        String orderNo,
        String taskStatus,
        String orderStatus,
        String reviewer,
        String reviewComment,
        Instant completedAt
) {
}
