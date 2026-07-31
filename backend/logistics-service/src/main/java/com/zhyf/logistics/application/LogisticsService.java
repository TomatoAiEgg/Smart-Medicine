package com.zhyf.logistics.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.status.OrderStatus;
import com.zhyf.logistics.infrastructure.CallbackClient;
import com.zhyf.logistics.infrastructure.LogisticsRepository;
import com.zhyf.logistics.infrastructure.OrderStatusClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LogisticsService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final DateTimeFormatter LEGACY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

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

    public List<LogisticsRecords.DeliveryOrderRecord> listReadyOrders(LogisticsShipmentQuery query) {
        return repository.findDecoctedOrders(normalizeQuery(query));
    }

    public List<LogisticsRecords.ShipmentRecord> listShipments(LogisticsShipmentQuery query) {
        return repository.findShipments(normalizeQuery(query));
    }

    public List<LogisticsRecords.ShipmentTraceRecord> listTraces(UUID shipmentId) {
        return repository.findTraces(shipmentId);
    }

    public List<LogisticsRecords.LogisticsInfoRecord> listLogisticsInfos(LogisticsShipmentQuery query) {
        return repository.findLogisticsInfos(normalizeQuery(query));
    }

    public Map<String, Object> queryBillPrintInfo(String orderNo) {
        LogisticsRecords.ShipmentRecord shipment = requireShipmentByOrderNo(orderNo);
        Map<String, Object> result = waybillPayload(shipment);
        result.put("printStatus", "READY");
        result.put("message", "系统已生成物流单，可生成浏览器面单；承运商电子面单需配置外部服务后下发");
        return result;
    }

    public Map<String, Object> printWaybill(String orderNo, String templateCode) {
        LogisticsRecords.ShipmentRecord shipment = requireShipmentByOrderNo(orderNo);
        Map<String, Object> result = waybillPayload(shipment);
        result.put("templateCode", StringUtils.hasText(templateCode) ? templateCode.trim() : "DEFAULT");
        result.put("printStatus", "PENDING_EXTERNAL_PROVIDER");
        result.put("message", "电子面单请求已进入系统契约，等待承运商服务配置后下发");
        return result;
    }

    public Map<String, Object> queryEmsPdfFile(String logisticsNo) {
        requireText(logisticsNo, "LOGISTICS_NO_REQUIRED", "Logistics no is required");
        LogisticsRecords.ShipmentRecord shipment = repository.findShipmentByLogisticsNo(logisticsNo)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment not found"));
        Map<String, Object> result = waybillPayload(shipment);
        result.put("pdfBase64", null);
        result.put("printStatus", "PENDING_EXTERNAL_PROVIDER");
        result.put("message", "EMS PDF 需外部 EMS 服务配置后获取");
        return result;
    }

    public Map<String, Object> queryLogisticsCost(String orderNo) {
        requireText(orderNo, "ORDER_NO_REQUIRED", "Order no is required");
        LogisticsRecords.LegacyLogisticsCostRecord cost = repository.findLegacyLogisticsCost(orderNo.trim())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", cost.orderNo());
        result.put("orderNo", cost.orderNo());
        result.put("externalOrderNo", cost.externalOrderNo());
        result.put("collectionMoney", money(cost.collectionMoney()));
        result.put("logisticsCompanyName", defaultValue(cost.logisticsCompanyName(), "SF"));
        result.put("payMethod", payMethod(cost.payMethod()));
        return result;
    }

    public List<Map<String, Object>> queryLogisticsInfo(Integer queryWay, String paramValue) {
        requireText(paramValue, "QUERY_PARAM_REQUIRED", "Query param is required");
        return repository.findLegacyRecipeInfos(queryWay, paramValue.trim()).stream()
                .map(this::legacyRecipeInfoPayload)
                .toList();
    }

    @Transactional
    public Map<String, Object> createLegacyLogisticsOrder(LogisticsCommands.LegacyLogisticsOrderCommand command) {
        requireText(command.orderNo(), "ORDER_NO_REQUIRED", "Order no is required");
        LogisticsRecords.ShipmentRecord shipment = pack(new LogisticsCommands.PackCommand(
                command.orderNo(),
                command.logisticsCompany(),
                command.logisticsNo(),
                command.payMethod(),
                command.pkgWeight(),
                command.pkgNum(),
                command.operator()
        ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("waybillNo", shipment.logisticsNo());
        result.put("routeCode", null);
        result.put("baseProductNo", command.logisticsCompany());
        result.put("orderId", shipment.orderNo());
        result.put("logisticsCompany", shipment.logisticsCompany());
        return result;
    }

    @Transactional
    public Map<String, Object> cancelLegacyLogisticsOrder(LogisticsCommands.LegacyLogisticsOrderCommand command) {
        requireText(command.orderNo(), "ORDER_NO_REQUIRED", "Order no is required");
        LogisticsRecords.ShipmentRecord shipment = repository.findShipmentByOrderNo(command.orderNo().trim())
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment not found"));
        if (!OrderStatus.PACKED.name().equals(shipment.logisticsStatus())) {
            throw new BusinessException("SHIPMENT_CANCEL_NOT_ALLOWED", "Only packed shipment can be cancelled");
        }
        Instant now = Instant.now(clock);
        repository.updateShipmentStatus(shipment.shipmentId(), OrderStatus.CANCELLED.name(), now);
        LogisticsRecords.ShipmentRecord cancelled = requireShipment(shipment.shipmentId());
        createTrace(cancelled, OrderStatus.CANCELLED.name(), defaultValue(command.remark(), "legacy logistics cancel"),
                null, now);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", cancelled.orderNo());
        result.put("waybillNo", cancelled.logisticsNo());
        result.put("cancelStatus", cancelled.logisticsStatus());
        return result;
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

    private LogisticsRecords.ShipmentRecord requireShipmentByOrderNo(String orderNo) {
        requireText(orderNo, "ORDER_NO_REQUIRED", "Order no is required");
        return repository.findShipmentByOrderNo(orderNo.trim())
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment not found"));
    }

    private Map<String, Object> waybillPayload(LogisticsRecords.ShipmentRecord shipment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shipmentId", shipment.shipmentId());
        payload.put("orderId", shipment.orderNo());
        payload.put("orderNo", shipment.orderNo());
        payload.put("externalOrderNo", shipment.externalOrderNo());
        payload.put("logisticsNo", shipment.logisticsNo());
        payload.put("waybillNo", shipment.logisticsNo());
        payload.put("logisticsCompany", shipment.logisticsCompany());
        payload.put("logisticsStatus", shipment.logisticsStatus());
        payload.put("logisticsPayMethod", shipment.payMethod());
        payload.put("receiverName", shipment.receiverName());
        payload.put("receiverPhone", shipment.receiverPhone());
        payload.put("receiverAddress", shipment.receiverAddress());
        payload.put("patientName", shipment.patientName());
        payload.put("institutionName", shipment.institutionName());
        payload.put("pkgWeight", shipment.pkgWeight());
        payload.put("pkgNum", shipment.pkgNum());
        payload.put("deliveryTime", shipment.deliveryTime());
        return payload;
    }

    private Map<String, Object> legacyRecipeInfoPayload(LogisticsRecords.LegacyRecipeInfoRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", record.orderNo());
        payload.put("recipeId", record.prescriptionNo());
        payload.put("orderStatus", record.orderStatus());
        payload.put("orderTime", legacyTime(record.orderTime()));
        payload.put("logisticsCompanyName", record.logisticsCompanyName());
        payload.put("logisticsNo", record.logisticsNo());
        payload.put("patientName", record.patientName());
        payload.put("recipeDiagnose", record.diagnosis());
        payload.put("medMethod", record.medicationMethod());
        payload.put("recipeRemark", record.prescriptionRemark());
        payload.put("amount", record.doseCount() == null ? null : String.valueOf(record.doseCount()));
        payload.put("lastLogisticsInfo", record.lastLogisticsInfo());
        payload.put("logisticsList", repository.findLegacyRecipeLogisticsInfos(record.orderId()).stream()
                .map(this::legacyRecipeTracePayload)
                .toList());
        return payload;
    }

    private Map<String, Object> legacyRecipeTracePayload(LogisticsRecords.LegacyRecipeLogisticsInfoRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("processStatus", record.processStatus());
        payload.put("operater", defaultValue(record.operator(), "system"));
        payload.put("operaTime", legacyTime(record.operationTime()));
        payload.put("operationInfo", record.operationInfo());
        payload.put("imageUrl", record.imageUrl());
        return payload;
    }

    private String money(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private int payMethod(String value) {
        if (!StringUtils.hasText(value)) {
            return 1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private String legacyTime(Instant value) {
        return value == null ? null : LEGACY_TIME_FORMATTER.format(value);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private LogisticsShipmentQuery normalizeQuery(LogisticsShipmentQuery query) {
        if (query == null) {
            return new LogisticsShipmentQuery(
                    null, null, null, null, null, null, null, null, null, null, null, null, DEFAULT_LIMIT
            );
        }
        return query.withLimit(normalizeLimit(query.limit()));
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
