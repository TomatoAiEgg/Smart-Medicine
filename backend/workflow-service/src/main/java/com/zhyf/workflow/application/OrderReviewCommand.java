package com.zhyf.workflow.application;

public record OrderReviewCommand(
        String reviewer,
        String reviewComment
) {
}
