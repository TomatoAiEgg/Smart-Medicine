package com.zhyf.order.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.status.OrderStatus;
import com.zhyf.order.domain.OrderSnapshot;
import com.zhyf.order.domain.OrderStatusMachine;
import com.zhyf.order.domain.OrderStatusTransition;
import com.zhyf.order.domain.WorkflowTaskSnapshot;
import com.zhyf.order.infrastructure.OrderRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderReviewTaskService {

    private final OrderRepository orderRepository;
    private final OrderStatusMachine orderStatusMachine;

    @Autowired
    public OrderReviewTaskService(OrderRepository orderRepository) {
        this(orderRepository, new OrderStatusMachine());
    }

    OrderReviewTaskService(OrderRepository orderRepository, OrderStatusMachine orderStatusMachine) {
        this.orderRepository = orderRepository;
        this.orderStatusMachine = orderStatusMachine;
    }

    public List<WorkflowTaskSnapshot> listPendingReviewTasks() {
        return orderRepository.findPendingReviewTasks();
    }

    @Transactional
    public OrderReviewResult approve(UUID taskId, OrderReviewCommand command) {
        return review(taskId, command, true);
    }

    @Transactional
    public OrderReviewResult reject(UUID taskId, OrderReviewCommand command) {
        return review(taskId, command, false);
    }

    private OrderReviewResult review(UUID taskId, OrderReviewCommand command, boolean approved) {
        WorkflowTaskSnapshot task = orderRepository.findReviewTaskById(taskId)
                .orElseThrow(() -> new BusinessException("REVIEW_TASK_NOT_FOUND", "Review task not found"));
        if (!"ORDER_REVIEW".equals(task.taskType())) {
            throw new BusinessException("REVIEW_TASK_TYPE_INVALID", "Review task type is invalid");
        }
        if (!"PENDING".equals(task.taskStatus())) {
            throw new BusinessException("REVIEW_TASK_ALREADY_HANDLED", "Review task already handled");
        }
        if (!StringUtils.hasText(command.reviewer())) {
            throw new BusinessException("REVIEWER_REQUIRED", "Reviewer is required");
        }

        OrderSnapshot order = orderRepository.findOrderById(task.orderId())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found"));
        OrderStatus targetStatus = approved ? OrderStatus.AUDIT_PASSED : OrderStatus.AUDIT_FAILED;
        OrderStatusTransition transition = requireTransition(order.status(), targetStatus);

        int updated = orderRepository.updateOrderStatus(order.orderId(), transition.toStatusName());
        if (updated == 0) {
            throw new BusinessException("ORDER_STATUS_UPDATE_FAILED", "Order status update failed");
        }
        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                task.tenantId(),
                order.orderId(),
                transition.fromStatusName(),
                transition.toStatusName(),
                "AUDIT",
                approved ? "workflow-review-approve" : "workflow-review-reject"
        );
        int taskUpdated = orderRepository.updateWorkflowTaskReviewResult(
                task.taskId(),
                approved ? "APPROVED" : "REJECTED",
                command.reviewer(),
                normalizeComment(command.reviewComment())
        );
        if (taskUpdated == 0) {
            throw new BusinessException("REVIEW_TASK_UPDATE_FAILED", "Review task update failed");
        }

        return new OrderReviewResult(
                task.taskId(),
                order.orderId(),
                order.orderNo(),
                approved ? "APPROVED" : "REJECTED",
                transition.toStatusName(),
                command.reviewer(),
                normalizeComment(command.reviewComment()),
                task.createdAt()
        );
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

    private String normalizeComment(String comment) {
        return StringUtils.hasText(comment) ? comment : null;
    }
}
