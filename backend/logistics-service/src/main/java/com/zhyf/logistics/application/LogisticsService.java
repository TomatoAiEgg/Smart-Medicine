package com.zhyf.logistics.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.status.OrderStatus;
import com.zhyf.logistics.infrastructure.CallbackClient;
import com.zhyf.logistics.infrastructure.LogisticsRepository;
import com.zhyf.logistics.infrastructure.OrderStatusClient;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LogisticsService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final LogisticsRepository repository;
    private final OrderStatusClient orderStatusClient;
    private final CallbackClient callbackClient;
    private final Clock clock;

    @Autowired
    public LogisticsService(
            LogisticsRepository repository,
            OrderStatusClient orderStatusClient,
            CallbackClient callbackClient
    ) {
        this(repository, orderStatusClient, callbackClient, Clock.systemUTC());
    }

    LogisticsService(
            LogisticsRepository repository,
            OrderStatusClient orderStatusClient,
            CallbackClient callbackClient,
            Clock clock
    ) {
        this.repository = repository;
        this.orderStatusClient = orderStatusClient;
        this.callbackClient = callbackClient;
        this.clock = clock;
    }

    public List<LogisticsRecords.DeliveryOrderRecord> listReadyOrders(int limit) {
        return repository.findDecoctedOrders(normalizeLimit(limit));
    }

    public List<LogisticsRecords.ShipmentRecord> listShipments(String status, String orderNo, int limit) {
        return repository.findShipments(status, orderNo, normalizeLimit(limit));
    }

    public List<LogisticsRecords.ShipmentTraceRecord> listTraces(UUID shipmentId) {
        return repository.findTraces(shipmentId);
    }

    @Transactional
    public LogisticsRecords.ShipmentRecord pack(LogisticsCommands.PackCommand command) {
        requireText(command.orderNo(), "ORDER_NO_REQUIRED", "Order no is required");
        LogisticsRecords.DeliveryOrderRecord order = repository.findOrderByOrderNo(command.orderNo())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found"));
        if (!OrderStatus.DECOCTED.name().equals(order.orderStatus())) {
            throw new BusinessException("ORDER_NOT_READY_FOR_PACK", "Only decocted order can be packed");
        }
        return repository.findShipmentByOrderId(order.orderId())
                .orElseGet(() -> createPackedShipment(command, order));
    }

    @Transactional
    public LogisticsRecords.ShipmentRecord ship(UUID shipmentId, LogisticsCommands.ShipmentActionCommand command) {
        LogisticsRecords.ShipmentRecord shipment = requireShipment(shipmentId);
        if (!OrderStatus.PACKED.name().equals(shipment.logisticsStatus())) {
            throw new BusinessException("SHIPMENT_STATUS_INVALID", "Only packed shipment can be shipped");
        }
        return advanceShipment(shipment, OrderStatus.SHIPPED.name(), "logistics-ship",
                command == null ? null : command.remark());
    }

    @Transactional
    public LogisticsRecords.ShipmentRecord sign(UUID shipmentId, LogisticsCommands.ShipmentActionCommand command) {
        LogisticsRecords.ShipmentRecord shipment = requireShipment(shipmentId);
        if (!List.of(OrderStatus.PACKED.name(), OrderStatus.SHIPPED.name(), OrderStatus.IN_TRANSIT.name())
                .contains(shipment.logisticsStatus())) {
            throw new BusinessException("SHIPMENT_STATUS_INVALID", "Shipment cannot be signed from current status");
        }
        return advanceShipment(shipment, OrderStatus.SIGNED.name(), "logistics-sign",
                command == null ? null : command.remark());
    }

    @Transactional
    public LogisticsRecords.ShipmentRecord receiveTrace(LogisticsCommands.TraceCommand command) {
        requireText(command.logisticsNo(), "LOGISTICS_NO_REQUIRED", "Logistics no is required");
        requireText(command.opCode(), "OP_CODE_REQUIRED", "Trace op code is required");
        LogisticsRecords.ShipmentRecord shipment = repository.findShipmentByLogisticsNo(command.logisticsNo())
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment not found"));
        String targetStatus = mapTraceStatus(command.provider(), command.opCode(), shipment.logisticsStatus());
        if (targetStatus == null || targetStatus.equals(shipment.logisticsStatus())) {
            String traceStatus = targetStatus == null ? shipment.logisticsStatus() : targetStatus;
            createTrace(shipment, traceStatus, command.traceContent(), command.rawPayload(), command.traceTime());
            return shipment;
        }
        return advanceShipment(shipment, targetStatus, "logistics-trace-" + command.opCode(), command.traceContent());
    }

    private LogisticsRecords.ShipmentRecord createPackedShipment(
            LogisticsCommands.PackCommand command,
            LogisticsRecords.DeliveryOrderRecord order
    ) {
        Instant now = Instant.now(clock);
        String logisticsNo = StringUtils.hasText(command.logisticsNo())
                ? command.logisticsNo().trim()
                : "MOCK-" + order.orderNo();
        String logisticsCompany = defaultValue(command.logisticsCompany(), "MOCK");
        UUID shipmentId = UUID.randomUUID();
        repository.createShipment(
                shipmentId,
                order,
                logisticsNo,
                logisticsCompany,
                command.payMethod(),
                command.pkgWeight(),
                command.pkgNum(),
                now
        );
        LogisticsRecords.ShipmentRecord shipment = requireShipment(shipmentId);
        orderStatusClient.updateStatus(order.orderId(), OrderStatus.PACKED.name(), "logistics-pack");
        createTrace(shipment, OrderStatus.PACKED.name(), "packed", null, now);
        callbackClient.createCallback(order.orderId(), "ORDER_PACKED", shipment.shipmentId().toString(),
                OrderStatus.PACKED.name(), "logistics-service");
        return requireShipment(shipmentId);
    }

    private LogisticsRecords.ShipmentRecord advanceShipment(
            LogisticsRecords.ShipmentRecord shipment,
            String targetStatus,
            String source,
            String traceContent
    ) {
        if (targetStatus.equals(shipment.logisticsStatus())) {
            return shipment;
        }
        Instant now = Instant.now(clock);
        orderStatusClient.updateStatus(shipment.orderId(), targetStatus, source);
        repository.updateShipmentStatus(shipment.shipmentId(), targetStatus, now);
        LogisticsRecords.ShipmentRecord nextShipment = requireShipment(shipment.shipmentId());
        createTrace(nextShipment, targetStatus, defaultValue(traceContent, targetStatus), null, now);
        callbackClient.createCallback(nextShipment.orderId(), "ORDER_" + targetStatus,
                nextShipment.shipmentId() + ":" + targetStatus, targetStatus, "logistics-service");
        return nextShipment;
    }

    private void createTrace(
            LogisticsRecords.ShipmentRecord shipment,
            String traceStatus,
            String traceContent,
            String rawPayload,
            Instant traceTime
    ) {
        repository.createTrace(
                UUID.randomUUID(),
                shipment,
                traceStatus,
                traceContent,
                rawPayload == null ? "{\"source\":\"logistics-service\"}" : rawPayload,
                traceTime == null ? Instant.now(clock) : traceTime
        );
    }

    private String mapTraceStatus(String provider, String opCode, String currentStatus) {
        String normalizedProvider = StringUtils.hasText(provider) ? provider.trim().toUpperCase() : "SF";
        String normalizedCode = opCode.trim();
        if ("SF".equals(normalizedProvider) || "SHUNFENG".equals(normalizedProvider)) {
            if (List.of("50", "43", "54").contains(normalizedCode)) {
                return OrderStatus.SHIPPED.name();
            }
            if (List.of("80", "8000").contains(normalizedCode)) {
                return OrderStatus.SIGNED.name();
            }
        }
        if ("EMS".equals(normalizedProvider)) {
            if ("203".equals(normalizedCode)) {
                return OrderStatus.SHIPPED.name();
            }
            if (List.of("748", "704").contains(normalizedCode)) {
                return OrderStatus.SIGNED.name();
            }
        }
        if (OrderStatus.SHIPPED.name().equals(currentStatus)) {
            return OrderStatus.IN_TRANSIT.name();
        }
        return null;
    }

    private LogisticsRecords.ShipmentRecord requireShipment(UUID shipmentId) {
        return repository.findShipmentById(shipmentId)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment not found"));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private void requireText(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(code, message);
        }
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
