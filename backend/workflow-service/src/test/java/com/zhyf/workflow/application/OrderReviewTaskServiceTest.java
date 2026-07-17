package com.zhyf.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.common.exception.BusinessException;
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
    private final PrescriptionDispenseTaskService dispenseTaskService = Mockito.mock(PrescriptionDispenseTaskService.class);
    private final Instant now = Instant.parse("2026-06-25T12:00:00Z");
    private final OrderReviewTaskService service = new OrderReviewTaskService(
            taskRepository,
            orderStatusClient,
            dispenseTaskService,
            Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void shouldApprovePendingReviewTaskThroughOrderServiceAndCreateDispenseTask() {
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
        verify(dispenseTaskService).createPendingDispenseTask(task, "order-review-approved");
    }
}

class PrescriptionDispenseTaskServiceTest {

    private final WorkflowTaskRepository taskRepository = Mockito.mock(WorkflowTaskRepository.class);
    private final PrescriptionRecheckTaskService recheckTaskService = Mockito.mock(PrescriptionRecheckTaskService.class);
    private final Instant now = Instant.parse("2026-06-26T06:00:00Z");
    private final PrescriptionDispenseTaskService service = new PrescriptionDispenseTaskService(
            taskRepository,
            recheckTaskService,
            Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void shouldCreatePendingDispenseTaskFromApprovedReviewTask() {
        WorkflowTaskSnapshot reviewTask = task("ORDER_REVIEW", "APPROVED", "AUDIT_PASSED");
        when(taskRepository.createWorkflowTask(
                any(UUID.class),
                eq(reviewTask.tenantId()),
                eq(reviewTask.orderId()),
                eq("PRESCRIPTION_DISPENSE"),
                eq(reviewTask.taskId().toString()),
                any(String.class)
        )).thenReturn(1);

        int created = service.createPendingDispenseTask(reviewTask, "order-review-approved");

        assertThat(created).isEqualTo(1);
    }

    @Test
    void shouldCompleteDispenseTaskAndCreateRecheckTask() {
        WorkflowTaskSnapshot dispenseTask = task("PRESCRIPTION_DISPENSE", "PENDING", "AUDIT_PASSED");
        when(taskRepository.findReviewTaskById(dispenseTask.taskId())).thenReturn(java.util.Optional.of(dispenseTask));
        when(taskRepository.createDispenseRecord(
                any(UUID.class),
                eq(dispenseTask.tenantId()),
                eq(dispenseTask.orderId()),
                eq(dispenseTask.taskId()),
                eq("dispenser1"),
                eq("printed"),
                eq(now)
        )).thenReturn(1);
        when(taskRepository.updateWorkflowTaskReviewResult(
                dispenseTask.taskId(),
                "COMPLETED",
                "dispenser1",
                "printed"
        )).thenReturn(1);

        OrderReviewResult result = service.complete(dispenseTask.taskId(), new OrderReviewCommand("dispenser1", "printed"));

        assertThat(result.taskId()).isEqualTo(dispenseTask.taskId());
        assertThat(result.taskStatus()).isEqualTo("COMPLETED");
        assertThat(result.orderStatus()).isEqualTo("AUDIT_PASSED");
        assertThat(result.completedAt()).isEqualTo(now);
        verify(recheckTaskService).createPendingRecheckTask(dispenseTask, "prescription-dispense-completed");
    }

    @Test
    void shouldStopWhenDispenseRecordCannotBeCreated() {
        WorkflowTaskSnapshot dispenseTask = task("PRESCRIPTION_DISPENSE", "PENDING", "AUDIT_PASSED");
        when(taskRepository.findReviewTaskById(dispenseTask.taskId())).thenReturn(java.util.Optional.of(dispenseTask));
        when(taskRepository.createDispenseRecord(
                any(UUID.class),
                eq(dispenseTask.tenantId()),
                eq(dispenseTask.orderId()),
                eq(dispenseTask.taskId()),
                eq("dispenser1"),
                eq("printed"),
                eq(now)
        )).thenReturn(0);

        assertThatThrownBy(() -> service.complete(dispenseTask.taskId(), new OrderReviewCommand("dispenser1", "printed")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Dispense record create failed");

        verify(taskRepository, never()).updateWorkflowTaskReviewResult(any(UUID.class), any(String.class), any(String.class), any(String.class));
        verify(recheckTaskService, never()).createPendingRecheckTask(any(WorkflowTaskSnapshot.class), any(String.class));
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
