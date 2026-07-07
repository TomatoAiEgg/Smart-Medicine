package com.zhyf.order.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.status.OrderStatus;
import com.zhyf.order.domain.OrderSnapshot;
import com.zhyf.order.domain.OrderStatusMachine;
import com.zhyf.order.domain.OrderStatusTransition;
import com.zhyf.order.infrastructure.OrderRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderStatusUpdateService {

    private final OrderRepository orderRepository;
    private final OrderStatusMachine orderStatusMachine;

    @Autowired
    public OrderStatusUpdateService(OrderRepository orderRepository) {
        this(orderRepository, new OrderStatusMachine());
    }

    OrderStatusUpdateService(OrderRepository orderRepository, OrderStatusMachine orderStatusMachine) {
        this.orderRepository = orderRepository;
        this.orderStatusMachine = orderStatusMachine;
    }

    @Transactional
    public OrderStatusUpdateResult updateStatus(UUID orderId, OrderStatusUpdateCommand command) {
        if (!StringUtils.hasText(command.targetStatus())) {
            throw new BusinessException("TARGET_STATUS_REQUIRED", "Target status is required");
        }
        OrderStatus targetStatus = parseTargetStatus(command.targetStatus());
        OrderSnapshot order = orderRepository.findOrderById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found"));
        OrderStatusTransition transition = requireTransition(order.status(), targetStatus);

        int updated = orderRepository.updateOrderStatus(order.orderId(), transition.toStatusName());
        if (updated == 0) {
            throw new BusinessException("ORDER_STATUS_UPDATE_FAILED", "Order status update failed");
        }
        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                order.tenantId(),
                order.orderId(),
                transition.fromStatusName(),
                transition.toStatusName(),
                defaultValue(command.operatorType(), "SYSTEM"),
                defaultValue(command.source(), "internal-status-update")
        );
        return new OrderStatusUpdateResult(
                order.orderId(),
                order.orderNo(),
                transition.fromStatusName(),
                transition.toStatusName()
        );
    }

    private OrderStatus parseTargetStatus(String targetStatus) {
        try {
            return orderStatusMachine.requireStatus(targetStatus);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("TARGET_STATUS_UNSUPPORTED", ex.getMessage());
        }
    }

    private OrderStatusTransition requireTransition(String currentStatus, OrderStatus targetStatus) {
        OrderStatus fromStatus;
        try {
            fromStatus = orderStatusMachine.requireStatus(currentStatus);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("ORDER_STATUS_INVALID", ex.getMessage());
        }
        try {
            return orderStatusMachine.requireTransition(fromStatus, targetStatus);
        } catch (IllegalStateException ex) {
            throw new BusinessException("ORDER_STATUS_TRANSITION_NOT_ALLOWED", ex.getMessage());
        }
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
