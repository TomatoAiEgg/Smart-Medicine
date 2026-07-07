package com.zhyf.logistics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.common.status.OrderStatus;
import com.zhyf.logistics.infrastructure.CallbackClient;
import com.zhyf.logistics.infrastructure.LogisticsRepository;
import com.zhyf.logistics.infrastructure.OrderStatusClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LogisticsServiceTest {

    private final LogisticsRepository repository = Mockito.mock(LogisticsRepository.class);
    private final OrderStatusClient orderStatusClient = Mockito.mock(OrderStatusClient.class);
    private final CallbackClient callbackClient = Mockito.mock(CallbackClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-07T00:00:00Z"), ZoneOffset.UTC);
    private final LogisticsService service = new LogisticsService(repository, orderStatusClient, callbackClient, clock);

    @Test
    void shouldPackDecoctedOrderAndCreateCallback() {
        UUID shipmentId = UUID.randomUUID();
        LogisticsRecords.DeliveryOrderRecord order = order(OrderStatus.DECOCTED.name());
        LogisticsRecords.ShipmentRecord shipment = shipment(shipmentId, order, OrderStatus.PACKED.name());
        when(repository.findOrderByOrderNo("ZHYF1")).thenReturn(Optional.of(order));
        when(repository.findShipmentByOrderId(order.orderId())).thenReturn(Optional.empty());
        when(repository.findShipmentById(any())).thenReturn(Optional.of(shipment));

        LogisticsRecords.ShipmentRecord result = service.pack(new LogisticsCommands.PackCommand(
                "ZHYF1",
                "SF",
                "SF-1",
                "MONTHLY",
                new BigDecimal("1.20"),
                1,
                "admin"
        ));

        assertThat(result.logisticsStatus()).isEqualTo("PACKED");
        verify(repository).createShipment(any(), eq(order), eq("SF-1"), eq("SF"), eq("MONTHLY"),
                eq(new BigDecimal("1.20")), eq(1), eq(Instant.now(clock)));
        verify(orderStatusClient).updateStatus(order.orderId(), "PACKED", "logistics-pack");
        verify(callbackClient).createCallback(order.orderId(), "ORDER_PACKED", shipmentId.toString(), "PACKED", "logistics-service");
    }

    @Test
    void shouldMapSfSignTraceToSigned() {
        UUID shipmentId = UUID.randomUUID();
        LogisticsRecords.DeliveryOrderRecord order = order(OrderStatus.SHIPPED.name());
        LogisticsRecords.ShipmentRecord shipment = shipment(shipmentId, order, OrderStatus.SHIPPED.name());
        LogisticsRecords.ShipmentRecord signed = shipment(shipmentId, order, OrderStatus.SIGNED.name());
        when(repository.findShipmentByLogisticsNo("SF-1")).thenReturn(Optional.of(shipment));
        when(repository.findShipmentById(shipmentId)).thenReturn(Optional.of(signed));

        LogisticsRecords.ShipmentRecord result = service.receiveTrace(new LogisticsCommands.TraceCommand(
                "SF-1",
                "SF",
                "80",
                "signed",
                "{}",
                Instant.now(clock),
                "sf"
        ));

        assertThat(result.logisticsStatus()).isEqualTo("SIGNED");
        verify(orderStatusClient).updateStatus(order.orderId(), "SIGNED", "logistics-trace-80");
    }

    @Test
    void shouldCreateTraceWhenProviderReportsCurrentStatusAgain() {
        UUID shipmentId = UUID.randomUUID();
        LogisticsRecords.DeliveryOrderRecord order = order(OrderStatus.SHIPPED.name());
        LogisticsRecords.ShipmentRecord shipment = shipment(shipmentId, order, OrderStatus.SHIPPED.name());
        when(repository.findShipmentByLogisticsNo("SF-1")).thenReturn(Optional.of(shipment));

        LogisticsRecords.ShipmentRecord result = service.receiveTrace(new LogisticsCommands.TraceCommand(
                "SF-1",
                "SF",
                "50",
                "shipping again",
                "{\"op\":\"50\"}",
                Instant.now(clock),
                "sf"
        ));

        assertThat(result.logisticsStatus()).isEqualTo("SHIPPED");
        verify(repository).createTrace(any(), eq(shipment), eq("SHIPPED"), eq("shipping again"),
                eq("{\"op\":\"50\"}"), eq(Instant.now(clock)));
    }

    private LogisticsRecords.DeliveryOrderRecord order(String status) {
        return new LogisticsRecords.DeliveryOrderRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ZHYF1",
                "EXT1",
                status,
                "receiver",
                "13800000000",
                "address"
        );
    }

    private LogisticsRecords.ShipmentRecord shipment(
            UUID shipmentId,
            LogisticsRecords.DeliveryOrderRecord order,
            String status
    ) {
        return new LogisticsRecords.ShipmentRecord(
                shipmentId,
                order.tenantId(),
                order.orderId(),
                order.orderNo(),
                "SF-1",
                "SF",
                status,
                "MONTHLY",
                new BigDecimal("1.20"),
                1,
                Instant.now(clock),
                null,
                null,
                Instant.now(clock),
                Instant.now(clock)
        );
    }
}
