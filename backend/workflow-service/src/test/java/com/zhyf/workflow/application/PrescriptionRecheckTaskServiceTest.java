package com.zhyf.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.zhyf.workflow.domain.WorkflowTaskSnapshot;
import com.zhyf.workflow.infrastructure.OrderStatusClient;
import com.zhyf.workflow.infrastructure.WorkflowTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class PrescriptionRecheckTaskServiceTest {

    private final WorkflowTaskRepository taskRepository = Mockito.mock(WorkflowTaskRepository.class);
    private final OrderStatusClient orderStatusClient = Mockito.mock(OrderStatusClient.class);
    private final Instant now = Instant.parse("2026-06-26T06:00:00Z");
    private final PrescriptionRecheckTaskService service = new PrescriptionRecheckTaskService(
            taskRepository,
            orderStatusClient,
            Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void shouldListPendingRecheckTasks() {
        WorkflowTaskSnapshot task = task("PRESCRIPTION_RECHECK", "PENDING", "AUDIT_PASSED");
        when(taskRepository.findPendingTasksByType("PRESCRIPTION_RECHECK")).thenReturn(List.of(task));

        assertThat(service.listPendingRecheckTasks()).containsExactly(task);
    }

    @Test
    void shouldCreatePendingRecheckTaskFromApprovedReviewTask() {
        WorkflowTaskSnapshot reviewTask = task("ORDER_REVIEW", "APPROVED", "AUDIT_PASSED");
        when(taskRepository.createWorkflowTask(
                ArgumentMatchers.any(UUID.class),
                ArgumentMatchers.eq(reviewTask.tenantId()),
                ArgumentMatchers.eq(reviewTask.orderId()),
                ArgumentMatchers.eq("PRESCRIPTION_RECHECK"),
                ArgumentMatchers.eq(reviewTask.taskId().toString()),
                ArgumentMatchers.contains("order-review-approved")
        )).thenReturn(1);

        int created = service.createPendingRecheckTask(reviewTask, "order-review-approved");

        assertThat(created).isEqualTo(1);
    }

    @Test
    void shouldCompleteRecheckTaskAndUpdateOrderStatus() {
        WorkflowTaskSnapshot recheckTask = task("PRESCRIPTION_RECHECK", "PENDING", "AUDIT_PASSED");
        when(taskRepository.findReviewTaskById(recheckTask.taskId())).thenReturn(Optional.of(recheckTask));
        when(orderStatusClient.updateStatus(
                recheckTask.orderId(),
                "RECHECKED",
                "RECHECK",
                "workflow-service-recheck-complete"
        )).thenReturn(new OrderStatusClient.OrderStatusUpdateResult(
                recheckTask.orderId(),
                "ZHYF1",
                "AUDIT_PASSED",
                "RECHECKED"
        ));
        when(taskRepository.updateWorkflowTaskReviewResult(
                recheckTask.taskId(),
                "COMPLETED",
                "rechecker1",
                "ok"
        )).thenReturn(1);

        OrderReviewResult result = service.complete(recheckTask.taskId(), new OrderReviewCommand("rechecker1", "ok"));

        assertThat(result.taskId()).isEqualTo(recheckTask.taskId());
        assertThat(result.taskStatus()).isEqualTo("COMPLETED");
        assertThat(result.orderStatus()).isEqualTo("RECHECKED");
        assertThat(result.completedAt()).isEqualTo(now);
    }

    private WorkflowTaskSnapshot task(String taskType, String taskStatus, String orderStatus) {
        return new WorkflowTaskSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                taskType,
                taskStatus,
                "event-1",
                null,
                null,
                "ZHYF1",
                "EXT1",
                orderStatus,
                "PASSED",
                "ok",
                Instant.now(),
                Instant.now(),
                null
        );
    }
}
