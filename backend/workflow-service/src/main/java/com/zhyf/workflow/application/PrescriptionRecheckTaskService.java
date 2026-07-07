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
public class PrescriptionRecheckTaskService {

    public static final String TASK_TYPE = "PRESCRIPTION_RECHECK";

    private final WorkflowTaskRepository taskRepository;
    private final OrderStatusClient orderStatusClient;
    private final Clock clock;

    @Autowired
    public PrescriptionRecheckTaskService(WorkflowTaskRepository taskRepository, OrderStatusClient orderStatusClient) {
        this(taskRepository, orderStatusClient, Clock.systemUTC());
    }

    PrescriptionRecheckTaskService(
            WorkflowTaskRepository taskRepository,
            OrderStatusClient orderStatusClient,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.orderStatusClient = orderStatusClient;
        this.clock = clock;
    }

    public List<WorkflowTaskSnapshot> listPendingRecheckTasks() {
        return taskRepository.findPendingTasksByType(TASK_TYPE);
    }

    public int createPendingRecheckTask(WorkflowTaskSnapshot reviewTask, String source) {
        String payload = """
                {"source":"%s","orderNo":"%s","externalOrderNo":"%s"}
                """.formatted(source, reviewTask.orderNo(), reviewTask.externalOrderNo());
        return taskRepository.createWorkflowTask(
                UUID.randomUUID(),
                reviewTask.tenantId(),
                reviewTask.orderId(),
                TASK_TYPE,
                reviewTask.taskId().toString(),
                payload
        );
    }

    @Transactional
    public OrderReviewResult complete(UUID taskId, OrderReviewCommand command) {
        WorkflowTaskSnapshot task = taskRepository.findReviewTaskById(taskId)
                .orElseThrow(() -> new BusinessException("RECHECK_TASK_NOT_FOUND", "Recheck task not found"));
        if (!TASK_TYPE.equals(task.taskType())) {
            throw new BusinessException("RECHECK_TASK_TYPE_INVALID", "Recheck task type is invalid");
        }
        if (!"PENDING".equals(task.taskStatus())) {
            throw new BusinessException("RECHECK_TASK_ALREADY_HANDLED", "Recheck task already handled");
        }
        if (!StringUtils.hasText(command.reviewer())) {
            throw new BusinessException("RECHECKER_REQUIRED", "Rechecker is required");
        }

        OrderStatusClient.OrderStatusUpdateResult orderResult = orderStatusClient.updateStatus(
                task.orderId(),
                OrderStatus.RECHECKED.name(),
                "RECHECK",
                "workflow-service-recheck-complete"
        );
        int taskUpdated = taskRepository.updateWorkflowTaskReviewResult(
                task.taskId(),
                "COMPLETED",
                command.reviewer(),
                normalizeComment(command.reviewComment())
        );
        if (taskUpdated == 0) {
            throw new BusinessException("RECHECK_TASK_UPDATE_FAILED", "Recheck task update failed");
        }

        return new OrderReviewResult(
                task.taskId(),
                task.orderId(),
                orderResult.orderNo(),
                "COMPLETED",
                orderResult.toStatus(),
                command.reviewer(),
                normalizeComment(command.reviewComment()),
                Instant.now(clock)
        );
    }

    private String normalizeComment(String comment) {
        return StringUtils.hasText(comment) ? comment : null;
    }
}
