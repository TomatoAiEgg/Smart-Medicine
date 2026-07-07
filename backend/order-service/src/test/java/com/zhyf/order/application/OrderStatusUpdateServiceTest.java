package com.zhyf.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.order.domain.OrderSnapshot;
import com.zhyf.order.domain.OrderStatusMachine;
import com.zhyf.order.infrastructure.OrderRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderStatusUpdateServiceTest {

    private final OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
    private final OrderStatusUpdateService service = new OrderStatusUpdateService(
            orderRepository,
            new OrderStatusMachine()
    );

    @Test
    void shouldUpdateCreatedOrderToAuditPassed() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OrderSnapshot order = new OrderSnapshot(
                orderId,
                tenantId,
                UUID.randomUUID(),
                "ZHYF1",
                "EXT1",
                "CREATED",
                Instant.now()
        );
        when(orderRepository.findOrderById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.updateOrderStatus(orderId, "AUDIT_PASSED")).thenReturn(1);

        OrderStatusUpdateResult result = service.updateStatus(
                orderId,
                new OrderStatusUpdateCommand("AUDIT_PASSED", "AUDIT", "workflow-service-review-approve")
        );

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.orderNo()).isEqualTo("ZHYF1");
        assertThat(result.fromStatus()).isEqualTo("CREATED");
        assertThat(result.toStatus()).isEqualTo("AUDIT_PASSED");
    }

    @Test
    void shouldRejectUnsupportedTargetStatus() {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> service.updateStatus(
                orderId,
                new OrderStatusUpdateCommand("UNKNOWN", "AUDIT", "test")
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Unsupported order status: UNKNOWN");
    }

    @Test
    void shouldRejectSkippedTransition() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OrderSnapshot order = new OrderSnapshot(
                orderId,
                tenantId,
                UUID.randomUUID(),
                "ZHYF1",
                "EXT1",
                "CREATED",
                Instant.now()
        );
        when(orderRepository.findOrderById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateStatus(
                orderId,
                new OrderStatusUpdateCommand("RECHECKED", "AUDIT", "test")
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Order status transition not allowed: CREATED -> RECHECKED");
    }
}
