package com.zhyf.order.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.status.OrderStatus;
import com.zhyf.order.domain.OrderSnapshot;
import com.zhyf.order.domain.OrderStatusMachine;
import com.zhyf.order.domain.OrderStatusTransition;
import com.zhyf.order.infrastructure.OrderRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

        int updated = orderRepository.updateOrderStatusIfCurrent(
                order.orderId(),
                transition.fromStatusName(),
                transition.toStatusName(),
                normalizeOptional(command.batchNo())
        );
        if (updated == 0) {
            throw new BusinessException("ORDER_STATUS_CONFLICT", "Order status changed, please refresh and retry");
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

    @Transactional
    public LegacyPdaLogisticsOutboundResult legacyPdaLogisticsOutbound(LegacyPdaLogisticsOutboundCommand command) {
        String recipeId = requireText(command == null ? null : command.recipeId(), "RECIPE_ID_REQUIRED",
                "Recipe id is required");
        OrderSnapshot order = orderRepository.findOrderByLegacyPdaRecipeId(recipeId)
                .orElseThrow(() -> new BusinessException("PDA_RECIPE_NOT_FOUND", "PDA recipe not found"));
        OrderStatus currentStatus = parseCurrentStatus(order.status());
        requireOutboundReady(currentStatus);
        int prescriptionCount = orderRepository.countActivePrescriptionsByOrderId(order.orderId());
        if (prescriptionCount > 1 && !"1".equals(defaultValue(command.operFlag(), ""))) {
            throw new BusinessException("ONE_ORDER_MORE_PRESCRIPTION", "Order has multiple prescriptions, confirm outbound first");
        }
        Instant outboundAt = command.outboundTime() == null ? Instant.now() : command.outboundTime();
        String operator = defaultValue(command.operator(), "legacy-pda");
        String logisticsNo = orderRepository.findShipmentNoByOrderId(order.orderId())
                .orElse("PDA-" + order.orderNo());
        orderRepository.upsertShippedShipment(
                UUID.randomUUID(),
                order.tenantId(),
                order.orderId(),
                order.orderNo(),
                logisticsNo,
                "PDA",
                outboundAt
        );
        advanceLegacyPdaOutboundStatus(order, currentStatus, operator);
        orderRepository.insertShipmentTrace(
                UUID.randomUUID(),
                order.tenantId(),
                order.orderId(),
                logisticsNo,
                "SHIPPED",
                "PDA物流出库完成",
                outboundAt
        );
        return new LegacyPdaLogisticsOutboundResult(
                order.orderId(),
                order.orderNo(),
                currentStatus.name(),
                OrderStatus.SHIPPED.name(),
                logisticsNo,
                outboundAt
        );
    }

    private void advanceLegacyPdaOutboundStatus(OrderSnapshot order, OrderStatus currentStatus, String operator) {
        OrderStatus from = currentStatus;
        for (OrderStatus target : legacyOutboundTargets(currentStatus)) {
            int updated = orderRepository.updateOrderStatusIfCurrent(order.orderId(), from.name(), target.name(), null);
            if (updated == 0) {
                throw new BusinessException("ORDER_STATUS_CONFLICT", "Order status changed, please refresh and retry");
            }
            orderRepository.insertOrderStatusLog(
                    UUID.randomUUID(),
                    order.tenantId(),
                    order.orderId(),
                    from.name(),
                    target.name(),
                    operator,
                    "legacy-pda-logistics-outbound"
            );
            from = target;
        }
    }

    private List<OrderStatus> legacyOutboundTargets(OrderStatus currentStatus) {
        List<OrderStatus> targets = new ArrayList<>();
        if (currentStatus == OrderStatus.RECHECKED) {
            targets.add(OrderStatus.DECOCTING);
            targets.add(OrderStatus.DECOCTED);
            targets.add(OrderStatus.PACKED);
        } else if (currentStatus == OrderStatus.DECOCTING) {
            targets.add(OrderStatus.DECOCTED);
            targets.add(OrderStatus.PACKED);
        } else if (currentStatus == OrderStatus.DECOCTED) {
            targets.add(OrderStatus.PACKED);
        }
        targets.add(OrderStatus.SHIPPED);
        return targets;
    }

    private void requireOutboundReady(OrderStatus status) {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.IN_TRANSIT || status == OrderStatus.SIGNED) {
            throw new BusinessException("ORDER_ALREADY_OUTBOUND", "Order is already outbound or signed");
        }
        if (status != OrderStatus.RECHECKED
                && status != OrderStatus.DECOCTING
                && status != OrderStatus.DECOCTED
                && status != OrderStatus.PACKED) {
            throw new BusinessException("ORDER_STATUS_NOT_OUTBOUND_READY", "Order status is not ready for outbound");
        }
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

    private OrderStatus parseCurrentStatus(String currentStatus) {
        try {
            return orderStatusMachine.requireStatus(currentStatus);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("ORDER_STATUS_INVALID", ex.getMessage());
        }
    }

    private String requireText(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
