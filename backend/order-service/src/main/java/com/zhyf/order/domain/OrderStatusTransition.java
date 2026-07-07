package com.zhyf.order.domain;

import com.zhyf.common.status.OrderStatus;

public record OrderStatusTransition(
        OrderStatus fromStatus,
        OrderStatus toStatus
) {

    public String fromStatusName() {
        return fromStatus.name();
    }

    public String toStatusName() {
        return toStatus.name();
    }
}
