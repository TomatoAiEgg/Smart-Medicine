package com.zhyf.workflow.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.workflow.domain.WorkflowTaskSnapshot;
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
public class PrescriptionDispenseTaskService {

    public static final String TASK_TYPE = "PRESCRIPTION_DISPENSE";

    private final WorkflowTaskRepository taskRepository;
    private final PrescriptionRecheckTaskService recheckTaskService;
    private final Clock clock;

    @Autowired
    public PrescriptionDispenseTaskService(
            WorkflowTaskRepository taskRepository,
            PrescriptionRecheckTaskService recheckTaskService
    ) {
        this(taskRepository, recheckTaskService, Clock.systemUTC());
    }

    PrescriptionDispenseTaskService(
            WorkflowTaskRepository taskRepository,
            PrescriptionRecheckTaskService recheckTaskService,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.recheckTaskService = recheckTaskService;
        this.clock = clock;
    }

    public List<WorkflowTaskSnapshot> listPendingDispenseTasks() {
        return taskRepository.findPendingTasksByType(TASK_TYPE);
    }

    public int createPendingDispenseTask(WorkflowTaskSnapshot reviewTask, String source) {
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
                .orElseThrow(() -> new BusinessException("DISPENSE_TASK_NOT_FOUND", "Dispense task not found"));
        if (!TASK_TYPE.equals(task.taskType())) {
            throw new BusinessException("DISPENSE_TASK_TYPE_INVALID", "Dispense task type is invalid");
        }
        if (!"PENDING".equals(task.taskStatus())) {
            throw new BusinessException("DISPENSE_TASK_ALREADY_HANDLED", "Dispense task already handled");
        }
        if (!StringUtils.hasText(command.reviewer())) {
            throw new BusinessException("DISPENSER_REQUIRED", "Dispenser is required");
        }
        Instant completedAt = Instant.now(clock);
        String comment = normalizeComment(command.reviewComment());
        int recordCreated = taskRepository.createDispenseRecord(
                UUID.randomUUID(),
                task.tenantId(),
                task.orderId(),
                task.taskId(),
                command.reviewer().trim(),
                comment,
                completedAt
        );
        if (recordCreated == 0) {
            throw new BusinessException("DISPENSE_RECORD_CREATE_FAILED", "Dispense record create failed");
        }
        int taskUpdated = taskRepository.updateWorkflowTaskReviewResult(
                task.taskId(),
                "COMPLETED",
                command.reviewer().trim(),
                comment
        );
        if (taskUpdated == 0) {
            throw new BusinessException("DISPENSE_TASK_UPDATE_FAILED", "Dispense task update failed");
        }
        recheckTaskService.createPendingRecheckTask(task, "prescription-dispense-completed");
        return new OrderReviewResult(
                task.taskId(),
                task.orderId(),
                task.orderNo(),
                "COMPLETED",
                task.orderStatus(),
                command.reviewer().trim(),
                comment,
                completedAt
        );
    }

    private String normalizeComment(String comment) {
        return StringUtils.hasText(comment) ? comment : null;
    }
}
