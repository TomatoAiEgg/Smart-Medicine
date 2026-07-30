package com.zhyf.workflow.application;

public record OrderReviewCommand(
        String reviewer,
        String reviewComment,
        String batchNo
) {
    public OrderReviewCommand(String reviewer, String reviewComment) {
        this(reviewer, reviewComment, null);
    }
}
