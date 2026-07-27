package com.zhyf.order.application;

import java.util.List;

public record AdminBatchOrderReceiptCommand(
        List<String> orderNos,
        String operator,
        String reason
) {
}
