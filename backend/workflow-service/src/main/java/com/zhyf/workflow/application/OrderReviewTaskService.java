package com.zhyf.workflow.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.status.OrderStatus;
import com.zhyf.workflow.domain.WorkflowTaskSnapshot;
import com.zhyf.workflow.infrastructure.OrderStatusClient;
import com.zhyf.workflow.infrastructure.WorkflowTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderReviewTaskService {

    private final WorkflowTaskRepository taskRepository;
    private final OrderStatusClient orderStatusClient;
    private final PrescriptionRecheckTaskService recheckTaskService;
    private final Clock clock;

    @Autowired
    public OrderReviewTaskService(
            WorkflowTaskRepository taskRepository,
            OrderStatusClient orderStatusClient,
            PrescriptionRecheckTaskService recheckTaskService
    ) {
        this(taskRepository, orderStatusClient, recheckTaskService, Clock.systemUTC());
    }

    OrderReviewTaskService(
            WorkflowTaskRepository taskRepository,
            OrderStatusClient orderStatusClient,
            PrescriptionRecheckTaskService recheckTaskService,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.orderStatusClient = orderStatusClient;
        this.recheckTaskService = recheckTaskService;
        this.clock = clock;
    }

    public List<WorkflowTaskSnapshot> listPendingReviewTasks() {
        return taskRepository.findPendingReviewTasks();
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
        WorkflowTaskSnapshot task = taskRepository.findReviewTaskById(taskId)
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

        OrderStatus targetStatus = approved ? OrderStatus.AUDIT_PASSED : OrderStatus.AUDIT_FAILED;
        String taskStatus = approved ? "APPROVED" : "REJECTED";
        String source = approved ? "workflow-service-review-approve" : "workflow-service-review-reject";
        OrderStatusClient.OrderStatusUpdateResult orderResult = orderStatusClient.updateStatus(
                task.orderId(),
                targetStatus.name(),
                "AUDIT",
                source
        );

        int taskUpdated = taskRepository.updateWorkflowTaskReviewResult(
                task.taskId(),
                taskStatus,
                command.reviewer(),
                normalizeComment(command.reviewComment())
        );
        if (taskUpdated == 0) {
            throw new BusinessException("REVIEW_TASK_UPDATE_FAILED", "Review task update failed");
        }
        if (approved) {
            recheckTaskService.createPendingRecheckTask(task, "order-review-approved");
        }
        Instant completedAt = Instant.now(clock);

        return new OrderReviewResult(
                task.taskId(),
                task.orderId(),
                orderResult.orderNo(),
                taskStatus,
                orderResult.toStatus(),
                command.reviewer(),
                normalizeComment(command.reviewComment()),
                completedAt
        );
    }

    private String normalizeComment(String comment) {
        return StringUtils.hasText(comment) ? comment : null;
    }
}
