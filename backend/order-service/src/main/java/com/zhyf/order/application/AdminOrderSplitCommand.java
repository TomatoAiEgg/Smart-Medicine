package com.zhyf.order.application;

import java.time.Instant;
import java.util.List;

public record AdminOrderSplitCommand(
        String prescriptionNo,
        List<SplitItem> items,
        String operator
) {
    public record SplitItem(
            Integer doseCount,
            Instant deliveryTime
    ) {
    }
}
