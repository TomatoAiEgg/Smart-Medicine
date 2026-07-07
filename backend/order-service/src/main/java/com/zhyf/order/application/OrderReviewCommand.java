package com.zhyf.order.application;

public record OrderReviewCommand(
        String reviewer,
        String reviewComment
) {
}
