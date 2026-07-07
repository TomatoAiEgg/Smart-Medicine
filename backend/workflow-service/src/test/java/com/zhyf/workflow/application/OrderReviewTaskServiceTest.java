package com.zhyf.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.workflow.domain.WorkflowTaskSnapshot;
import com.zhyf.workflow.infrastructure.OrderStatusClient;
import com.zhyf.workflow.infrastructure.WorkflowTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderReviewTaskServiceTest {

    private final WorkflowTaskRepository taskRepository = Mockito.mock(WorkflowTaskRepository.class);
    private final OrderStatusClient orderStatusClient = Mockito.mock(OrderStatusClient.class);
    private final PrescriptionRecheckTaskService recheckTaskService = Mockito.mock(PrescriptionRecheckTaskService.class);
    private final Instant now = Instant.parse("2026-06-25T12:00:00Z");
    private final OrderReviewTaskService service = new OrderReviewTaskService(
            taskRepository,
            orderStatusClient,
            recheckTaskService,
            Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void shouldApprovePendingReviewTaskThroughOrderServiceAndCreateRecheckTask() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        WorkflowTaskSnapshot task = new WorkflowTaskSnapshot(
                taskId,
                tenantId,
                orderId,
                "ORDER_REVIEW",
                "PENDING",
                "event-1",
                null,
                null,
                "ZHYF1",
                "EXT1",
                "CREATED",
                "PASSED",
                "basic validation passed",
                Instant.now(),
                Instant.now(),
                null
        );
        when(taskRepository.findReviewTaskById(taskId)).thenReturn(java.util.Optional.of(task));
        when(orderStatusClient.updateStatus(
                orderId,
                "AUDIT_PASSED",
                "AUDIT",
                "workflow-service-review-approve"
        )).thenReturn(new OrderStatusClient.OrderStatusUpdateResult(orderId, "ZHYF1", "CREATED", "AUDIT_PASSED"));
        when(taskRepository.updateWorkflowTaskReviewResult(taskId, "APPROVED", "reviewer1", "ok")).thenReturn(1);

        OrderReviewResult result = service.approve(taskId, new OrderReviewCommand("reviewer1", "ok"));

        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.taskStatus()).isEqualTo("APPROVED");
        assertThat(result.orderStatus()).isEqualTo("AUDIT_PASSED");
        assertThat(result.reviewer()).isEqualTo("reviewer1");
        assertThat(result.completedAt()).isEqualTo(now);
        verify(recheckTaskService).createPendingRecheckTask(task, "order-review-approved");
    }
}
