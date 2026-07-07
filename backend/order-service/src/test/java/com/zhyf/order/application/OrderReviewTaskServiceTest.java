package com.zhyf.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.order.domain.OrderSnapshot;
import com.zhyf.order.domain.OrderStatusMachine;
import com.zhyf.order.domain.WorkflowTaskSnapshot;
import com.zhyf.order.infrastructure.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderReviewTaskServiceTest {

    private final OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
    private final OrderReviewTaskService service = new OrderReviewTaskService(
            orderRepository,
            new OrderStatusMachine()
    );

    @Test
    void shouldListPendingReviewTasks() {
        WorkflowTaskSnapshot snapshot = new WorkflowTaskSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
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
        when(orderRepository.findPendingReviewTasks()).thenReturn(List.of(snapshot));

        assertThat(service.listPendingReviewTasks()).containsExactly(snapshot);
    }

    @Test
    void shouldApprovePendingReviewTask() {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
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
        OrderSnapshot order = new OrderSnapshot(orderId, tenantId, UUID.randomUUID(), "ZHYF1", "EXT1", "CREATED", Instant.now());
        when(orderRepository.findReviewTaskById(taskId)).thenReturn(Optional.of(task));
        when(orderRepository.findOrderById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.updateOrderStatus(orderId, "AUDIT_PASSED")).thenReturn(1);
        when(orderRepository.updateWorkflowTaskReviewResult(taskId, "APPROVED", "reviewer1", "ok")).thenReturn(1);

        var result = service.approve(taskId, new OrderReviewCommand("reviewer1", "ok"));

        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.orderStatus()).isEqualTo("AUDIT_PASSED");
        assertThat(result.taskStatus()).isEqualTo("APPROVED");
        assertThat(result.reviewer()).isEqualTo("reviewer1");
    }

    @Test
    void shouldRejectReviewWhenOrderStatusCannotTransition() {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
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
                "RECHECKED",
                "PASSED",
                "ok",
                Instant.now(),
                Instant.now(),
                null
        );
        OrderSnapshot order = new OrderSnapshot(orderId, tenantId, UUID.randomUUID(), "ZHYF1", "EXT1", "RECHECKED", Instant.now());
        when(orderRepository.findReviewTaskById(taskId)).thenReturn(Optional.of(task));
        when(orderRepository.findOrderById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.approve(taskId, new OrderReviewCommand("reviewer1", "ok")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Order status transition not allowed: RECHECKED -> AUDIT_PASSED");
    }
}
