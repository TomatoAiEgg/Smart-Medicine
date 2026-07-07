package com.zhyf.order.domain;

import com.zhyf.common.status.OrderStatus;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class OrderStatusMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.CREATED, Set.of(
                    OrderStatus.AUDIT_PASSED,
                    OrderStatus.AUDIT_FAILED,
                    OrderStatus.CANCELLED
            ),
            OrderStatus.AUDIT_PASSED, Set.of(
                    OrderStatus.RECHECKED,
                    OrderStatus.CANCELLED
            ),
            OrderStatus.RECHECKED, Set.of(
                    OrderStatus.DECOCTING,
                    OrderStatus.CANCELLED
            ),
            OrderStatus.DECOCTING, Set.of(
                    OrderStatus.DECOCTED,
                    OrderStatus.CANCELLED
            ),
            OrderStatus.DECOCTED, Set.of(OrderStatus.PACKED),
            OrderStatus.PACKED, Set.of(
                    OrderStatus.SHIPPED,
                    OrderStatus.SIGNED
            ),
            OrderStatus.SHIPPED, Set.of(
                    OrderStatus.IN_TRANSIT,
                    OrderStatus.SIGNED
            ),
            OrderStatus.IN_TRANSIT, Set.of(OrderStatus.SIGNED)
    );

    public OrderStatus requireStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Order status is blank");
        }
        try {
            return OrderStatus.valueOf(status.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported order status: " + status, ex);
        }
    }

    public OrderStatusTransition requireTransition(OrderStatus fromStatus, OrderStatus toStatus) {
        Objects.requireNonNull(fromStatus, "fromStatus must not be null");
        Objects.requireNonNull(toStatus, "toStatus must not be null");
        if (!canTransition(fromStatus, toStatus)) {
            throw new IllegalStateException(
                    "Order status transition not allowed: " + fromStatus.name() + " -> " + toStatus.name()
            );
        }
        return new OrderStatusTransition(fromStatus, toStatus);
    }

    public boolean canTransition(OrderStatus fromStatus, OrderStatus toStatus) {
        return ALLOWED_TRANSITIONS.getOrDefault(fromStatus, Set.of()).contains(toStatus);
    }

    public Set<OrderStatus> nextStatuses(OrderStatus fromStatus) {
        return ALLOWED_TRANSITIONS.getOrDefault(fromStatus, Set.of());
    }
}
