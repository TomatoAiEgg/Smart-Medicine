package com.zhyf.order.application;

import java.util.List;

public record AdminOrderSplitResult(
        String originalOrderNo,
        String prescriptionNo,
        List<String> splitOrderNos,
        String status
) {
}
