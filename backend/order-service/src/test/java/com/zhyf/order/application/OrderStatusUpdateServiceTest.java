package com.zhyf.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
        when(orderRepository.updateOrderStatusIfCurrent(orderId, "CREATED", "AUDIT_PASSED", null)).thenReturn(1);

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
    void shouldUpdateBatchNoWithStatusWhenProvided() {
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
        when(orderRepository.updateOrderStatusIfCurrent(orderId, "CREATED", "AUDIT_PASSED", "1")).thenReturn(1);

        OrderStatusUpdateResult result = service.updateStatus(
                orderId,
                new OrderStatusUpdateCommand("AUDIT_PASSED", "AUDIT", "workflow-service-review-approve", "1")
        );

        assertThat(result.toStatus()).isEqualTo("AUDIT_PASSED");
    }

    @Test
    void shouldUpdateAuditPassedOrderToAuditFailedForPrescriptionRecheckReject() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OrderSnapshot order = new OrderSnapshot(
                orderId,
                tenantId,
                UUID.randomUUID(),
                "ZHYF1",
                "EXT1",
                "AUDIT_PASSED",
                Instant.now()
        );
        when(orderRepository.findOrderById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.updateOrderStatusIfCurrent(orderId, "AUDIT_PASSED", "AUDIT_FAILED", null)).thenReturn(1);

        OrderStatusUpdateResult result = service.updateStatus(
                orderId,
                new OrderStatusUpdateCommand("AUDIT_FAILED", "AUDIT", "workflow-service-review-reject")
        );

        assertThat(result.fromStatus()).isEqualTo("AUDIT_PASSED");
        assertThat(result.toStatus()).isEqualTo("AUDIT_FAILED");
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
    void shouldRejectStatusConflictWhenCurrentStatusChangedBeforeUpdate() {
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
        when(orderRepository.updateOrderStatusIfCurrent(orderId, "CREATED", "AUDIT_PASSED", null)).thenReturn(0);

        assertThatThrownBy(() -> service.updateStatus(
                orderId,
                new OrderStatusUpdateCommand("AUDIT_PASSED", "AUDIT", "workflow-service-review-approve")
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Order status changed, please refresh and retry");
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

    @Test
    void shouldOutboundLegacyPdaOrderAndAdvanceStatusChain() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant outboundAt = Instant.parse("2026-07-31T12:00:00Z");
        OrderSnapshot order = new OrderSnapshot(
                orderId,
                tenantId,
                UUID.randomUUID(),
                "ZHYF1",
                "EXT1",
                "DECOCTED",
                Instant.now()
        );
        when(orderRepository.findOrderByLegacyPdaRecipeId("RX1")).thenReturn(Optional.of(order));
        when(orderRepository.countActivePrescriptionsByOrderId(orderId)).thenReturn(2);
        when(orderRepository.findShipmentNoByOrderId(orderId)).thenReturn(Optional.of("SF1"));
        when(orderRepository.updateOrderStatusIfCurrent(orderId, "DECOCTED", "PACKED", null)).thenReturn(1);
        when(orderRepository.updateOrderStatusIfCurrent(orderId, "PACKED", "SHIPPED", null)).thenReturn(1);

        LegacyPdaLogisticsOutboundResult result = service.legacyPdaLogisticsOutbound(
                new LegacyPdaLogisticsOutboundCommand("RX1", "1", "pda-user", outboundAt)
        );

        assertThat(result.orderNo()).isEqualTo("ZHYF1");
        assertThat(result.fromStatus()).isEqualTo("DECOCTED");
        assertThat(result.toStatus()).isEqualTo("SHIPPED");
        assertThat(result.logisticsNo()).isEqualTo("SF1");
        verify(orderRepository).upsertShippedShipment(
                any(UUID.class),
                eq(tenantId),
                eq(orderId),
                eq("ZHYF1"),
                eq("SF1"),
                eq("PDA"),
                eq(outboundAt)
        );
        verify(orderRepository).insertShipmentTrace(
                any(UUID.class),
                eq(tenantId),
                eq(orderId),
                eq("SF1"),
                eq("SHIPPED"),
                eq("PDA物流出库完成"),
                eq(outboundAt)
        );
    }

    @Test
    void shouldRejectLegacyPdaOutboundWhenMultiplePrescriptionsNotConfirmed() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OrderSnapshot order = new OrderSnapshot(
                orderId,
                tenantId,
                UUID.randomUUID(),
                "ZHYF1",
                "EXT1",
                "PACKED",
                Instant.now()
        );
        when(orderRepository.findOrderByLegacyPdaRecipeId("RX1")).thenReturn(Optional.of(order));
        when(orderRepository.countActivePrescriptionsByOrderId(orderId)).thenReturn(2);

        assertThatThrownBy(() -> service.legacyPdaLogisticsOutbound(
                new LegacyPdaLogisticsOutboundCommand("RX1", "0", "pda-user", Instant.now())
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Order has multiple prescriptions, confirm outbound first");
    }
}
