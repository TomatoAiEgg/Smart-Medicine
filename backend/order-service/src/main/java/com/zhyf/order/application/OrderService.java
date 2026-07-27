package com.zhyf.order.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.security.SignatureUtils;
import com.zhyf.common.status.OrderStatus;
import com.zhyf.common.status.PrescriptionStatus;
import com.zhyf.order.domain.InstitutionApp;
import com.zhyf.order.domain.OrderProgressSnapshot;
import com.zhyf.order.infrastructure.OrderRepository;
import java.math.BigDecimal;
import java.time.format.DateTimeParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderService {

    private static final ZoneId DEFAULT_PAYLOAD_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LEGACY_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<OrderStatus> ADMIN_CANCELLABLE_STATUSES = EnumSet.of(
            OrderStatus.CREATED,
            OrderStatus.AUDIT_PASSED,
            OrderStatus.RECHECKED
    );

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderCreateResult createOrder(OrderCreateCommand command) {
        String rawPayload = writeJson(command.payload());
        InstitutionApp app = orderRepository.findEnabledApp(command.appKey())
                .orElseThrow(() -> new BusinessException("APP_NOT_FOUND", "机构应用不存在或已停用"));

        verifySignature(command, app, rawPayload);

        String externalOrderNo = readText(command.payload(), "externalOrderNo", "orderNo", "prescriptionOrderNo");
        if (!StringUtils.hasText(externalOrderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "外部订单号不能为空");
        }

        return orderRepository.findOrderByExternalNo(app.tenantId(), app.institutionId(), externalOrderNo)
                .map(existing -> new OrderCreateResult(
                        existing.orderId(),
                        existing.orderNo(),
                        externalOrderNo,
                        existing.status(),
                        true
                ))
                .orElseGet(() -> createNewOrder(app, externalOrderNo, command.payload(), rawPayload));
    }

    public OrderCreateResult getOrder(String orderNo) {
        return orderRepository.findOrderByOrderNo(orderNo)
                .map(existing -> new OrderCreateResult(
                        existing.orderId(),
                        existing.orderNo(),
                        existing.externalOrderNo(),
                        existing.status(),
                        false
                ))
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
    }

    public OrderProgressSnapshot getOrderProgress(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        return orderRepository.findOrderProgressByOrderNo(orderNo.trim())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
    }

    public AdminOrderDetail getAdminOrderDetail(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        return orderRepository.findAdminOrderDetailByOrderNo(orderNo.trim())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
    }

    public AdminOrderPage listAdminOrders(AdminOrderSearchQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        AdminOrderSearchQuery normalized = new AdminOrderSearchQuery(
                query.startTime(),
                query.endTime(),
                query.institution(),
                query.prescriptionType(),
                query.hospitalType(),
                query.orderStatus(),
                query.decoctionCenter(),
                query.deliveryType(),
                query.logisticsCompany(),
                query.province(),
                query.keyword(),
                query.hospitalPrescriptionNo(),
                query.patientName(),
                query.receiverPhone(),
                page,
                pageSize
        );
        return orderRepository.searchAdminOrders(normalized);
    }

    @Transactional
    public AdminOrderAddressUpdateResult updateAdminOrderAddress(
            String orderNo,
            AdminOrderAddressUpdateCommand command
    ) {
        if (command == null) {
            throw new BusinessException("ORDER_ADDRESS_COMMAND_REQUIRED", "地址修改参数不能为空");
        }
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        String receiverName = requireText(command.receiverName(), "RECEIVER_NAME_REQUIRED", "收货人不能为空");
        String receiverPhone = requireText(command.receiverPhone(), "RECEIVER_PHONE_REQUIRED", "收货电话不能为空");
        String receiverAddress = requireText(command.receiverAddress(), "RECEIVER_ADDRESS_REQUIRED", "详细地址不能为空");
        String normalizedOrderNo = orderNo.trim();
        Instant deliveryTime = readAddressDeliveryTime(command.deliveryTime());
        AdminOrderDetail current = getAdminOrderDetail(normalizedOrderNo);
        int updated = orderRepository.updateOrderAddress(
                current.orderId(),
                receiverName,
                receiverPhone,
                cleanText(command.receiverProvince()),
                cleanText(command.receiverCity()),
                cleanText(command.receiverZone()),
                receiverAddress,
                cleanText(command.addressType()),
                deliveryTime
        );
        if (updated == 0) {
            throw new BusinessException("ORDER_ADDRESS_UPDATE_FAILED", "订单地址更新失败");
        }
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                null,
                defaultText(command.operator(), "admin"),
                "ORDER_ADDRESS_UPDATE",
                "SUCCESS",
                cleanText(command.reason()),
                writeJson(command)
        );
        AdminOrderDetail next = getAdminOrderDetail(normalizedOrderNo);
        return new AdminOrderAddressUpdateResult(
                next.orderId(),
                next.orderNo(),
                next.receiverName(),
                next.receiverPhone(),
                next.receiverProvince(),
                next.receiverCity(),
                next.receiverZone(),
                next.receiverAddress(),
                next.addressType(),
                next.deliveryTime(),
                next.updatedAt()
        );
    }

    @Transactional
    public AdminOrderCancelResult cancelAdminOrder(String orderNo, AdminOrderCancelCommand command) {
        if (command == null) {
            throw new BusinessException("ORDER_CANCEL_COMMAND_REQUIRED", "取消参数不能为空");
        }
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        String reason = requireText(command.reason(), "ORDER_CANCEL_REASON_REQUIRED", "取消原因不能为空");
        String operator = defaultText(command.operator(), "admin");
        String normalizedOrderNo = orderNo.trim();
        AdminOrderDetail current = getAdminOrderDetail(normalizedOrderNo);
        OrderStatus currentStatus = parseOrderStatus(current.orderStatus());
        if (!canAdminCancel(currentStatus)) {
            throw new BusinessException("ORDER_CANCEL_NOT_ALLOWED", "当前订单状态不允许取消");
        }
        int updated = orderRepository.updateOrderStatusIfCurrent(
                current.orderId(),
                currentStatus.name(),
                OrderStatus.CANCELLED.name()
        );
        if (updated == 0) {
            throw new BusinessException("ORDER_CANCEL_CONFLICT", "订单状态已变更，请刷新后重试");
        }
        int cancelledPrescriptions = orderRepository.updatePrescriptionsStatusByOrderId(
                current.orderId(),
                PrescriptionStatus.CANCELLED.name()
        );
        int cancelledTasks = orderRepository.cancelPendingWorkflowTasks(current.orderId(), operator, reason);
        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                currentStatus.name(),
                OrderStatus.CANCELLED.name(),
                "ADMIN",
                "admin-order-cancel"
        );
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                null,
                operator,
                "ORDER_CANCEL",
                "SUCCESS",
                reason,
                writeJson(command)
        );
        return new AdminOrderCancelResult(
                current.orderId(),
                current.orderNo(),
                currentStatus.name(),
                OrderStatus.CANCELLED.name(),
                cancelledPrescriptions,
                cancelledTasks,
                Instant.now()
        );
    }

    private OrderCreateResult createNewOrder(
            InstitutionApp app,
            String externalOrderNo,
            JsonNode payload,
            String rawPayload
    ) {
        UUID orderId = UUID.randomUUID();
        String orderNo = "ZHYF" + Instant.now().toEpochMilli();
        String patientName = readText(payload, "patientName", "patient_name", "patient");
        String patientPhone = readText(payload, "patientPhone", "patient_phone", "patientTel");
        String receiverName = readText(payload, "receiverName", "consignee", "receiver");
        String receiverPhone = readText(payload, "receiverPhone", "conTel", "receiverTel");
        String receiverAddress = readText(payload, "receiverAddress", "address", "addrDetail");
        String receiverProvince = readText(payload, "receiverProvince", "province");
        String receiverCity = readText(payload, "receiverCity", "city");
        String receiverZone = readText(payload, "receiverZone", "zone");
        String addressType = readText(payload, "addressType", "addrType");

        orderRepository.insertOrder(
                orderId,
                app.tenantId(),
                app.institutionId(),
                orderNo,
                externalOrderNo,
                OrderStatus.CREATED.name(),
                patientName,
                patientPhone,
                receiverName,
                receiverPhone,
                receiverProvince,
                receiverCity,
                receiverZone,
                receiverAddress,
                addressType,
                readInstant(payload, "deliveryTime", "delivery_time"),
                readText(payload, "batchNo", "classes"),
                readText(payload, "orderRemark", "order_remark", "remark"),
                app.callbackUrl(),
                rawPayload
        );
        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                app.tenantId(),
                orderId,
                null,
                OrderStatus.CREATED.name(),
                "INSTITUTION",
                "createOrder"
        );

        List<UUID> prescriptionIds = createPrescriptions(app, orderId, payload);
        String eventPayload = """
                {"tenantId":"%s","orderId":"%s","orderNo":"%s","externalOrderNo":"%s","prescriptionIds":%s}
                """.formatted(app.tenantId(), orderId, orderNo, externalOrderNo, writeJson(prescriptionIds));
        orderRepository.insertOutbox(
                UUID.randomUUID(),
                app.tenantId(),
                UUID.randomUUID().toString(),
                "ORDER_CREATED",
                "ORDER",
                orderId.toString(),
                eventPayload
        );

        return new OrderCreateResult(orderId, orderNo, externalOrderNo, OrderStatus.CREATED.name(), false);
    }

    private List<UUID> createPrescriptions(InstitutionApp app, UUID orderId, JsonNode payload) {
        JsonNode prescriptions = payload.get("prescriptions");
        List<UUID> prescriptionIds = new ArrayList<>();
        if (prescriptions == null || !prescriptions.isArray() || prescriptions.isEmpty()) {
            UUID prescriptionId = insertPrescription(app, orderId, payload, 1);
            prescriptionIds.add(prescriptionId);
            return prescriptionIds;
        }

        int sort = 1;
        for (JsonNode prescription : prescriptions) {
            UUID prescriptionId = insertPrescription(app, orderId, prescription, sort++);
            prescriptionIds.add(prescriptionId);
        }
        return prescriptionIds;
    }

    private UUID insertPrescription(InstitutionApp app, UUID orderId, JsonNode node, int sort) {
        UUID prescriptionId = UUID.randomUUID();
        String externalPrescriptionNo = readText(node, "externalPrescriptionNo", "prescriptionNo", "presNum");
        if (!StringUtils.hasText(externalPrescriptionNo)) {
            externalPrescriptionNo = "PRES-" + orderId + "-" + sort;
        }
        String prescriptionNo = "RX" + Instant.now().toEpochMilli() + sort;
        orderRepository.insertPrescription(
                prescriptionId,
                app.tenantId(),
                app.institutionId(),
                orderId,
                prescriptionNo,
                externalPrescriptionNo,
                readText(node, "prescriptionType", "prescriType", "type"),
                PrescriptionStatus.CREATED.name(),
                readText(node, "hospitalType", "isHos"),
                readInteger(node, "doseCount", "amount", "herbsNum", "herbs_num"),
                readInteger(node, "decoctionCount", "decoctAmount", "decoct_amount"),
                readDecimal(node, "decoctionUnitPrice", "decoctUnitPrice", "decoct_unit_price"),
                readDecimal(node, "decoctionTotalPrice", "decoctTotalPrice", "decoct_total_price"),
                readDecimal(node, "totalAmount", "totalMoney", "total_money"),
                readText(node, "doctorName", "doctor"),
                readText(node, "diagnosis"),
                readText(node, "departmentName", "hosDepart", "hos_depart"),
                readText(node, "wardName", "hosAreaNo", "hos_area_no"),
                readText(node, "bedNo", "hosBedNo", "hos_bed_no"),
                readText(node, "medicationMethod", "medMethod", "med_method"),
                readText(node, "medicationInstruction", "medGuide", "med_guide"),
                readText(node, "prescriptionRemark", "prescriRemark", "prescri_remark"),
                writeJson(node)
        );

        JsonNode details = node.get("details");
        if (details == null) {
            details = node.get("drugs");
        }
        if (details != null && details.isArray()) {
            int detailSort = 1;
            for (JsonNode detail : details) {
                orderRepository.insertPrescriptionDetail(
                        UUID.randomUUID(),
                        app.tenantId(),
                        prescriptionId,
                        readText(detail, "drugCode", "medicineCode"),
                        readText(detail, "drugName", "medicineName"),
                        readText(detail, "platformDrugCode", "dcGoodsNum", "dc_goods_num", "ptGoodsNum", "pt_goods_num"),
                        readText(detail, "platformDrugName", "dcGoodsName", "dc_goods_name"),
                        readText(detail, "drugSpecs", "goodsNorms", "goods_norms", "specs"),
                        readText(detail, "drugOrigin", "goodsOrigin", "goods_origin", "origin"),
                        readText(detail, "dose", "dosage"),
                        readText(detail, "unit"),
                        readText(detail, "specialUsage", "special_usage"),
                        readDecimal(detail, "quantity", "goodsQuantity", "goods_quantity"),
                        readDecimal(detail, "unitPrice", "medSalePrice", "med_sale_price"),
                        readDecimal(detail, "settlementUnitPrice", "medSettlePrice", "med_settle_price"),
                        readDecimal(detail, "totalPrice", "lineTotalPrice", "line_total_price"),
                        readDecimal(detail, "settlementTotalPrice", "settlementTotalPrice", "settlement_total_price"),
                        readText(detail, "batchNo", "batch_no"),
                        readText(detail, "remark"),
                        readText(detail, "validationTips", "validation_tips"),
                        detailSort++
                );
            }
        }
        return prescriptionId;
    }

    private void verifySignature(OrderCreateCommand command, InstitutionApp app, String rawPayload) {
        String bodyHash = SignatureUtils.sha256Hex(rawPayload);
        String source = command.appKey() + "\n" + command.timestamp() + "\n" + bodyHash;
        String expected = SignatureUtils.hmacSha256Hex(app.appSecret(), source);
        if (!SignatureUtils.constantTimeEquals(expected, command.signature())) {
            throw new BusinessException("INVALID_SIGNATURE", "签名错误");
        }
    }

    private String readText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private String requireText(String value, String code, String message) {
        String cleaned = cleanText(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new BusinessException(code, message);
        }
        return cleaned;
    }

    private String cleanText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value, String fallback) {
        String cleaned = cleanText(value);
        return cleaned == null ? fallback : cleaned;
    }

    private OrderStatus parseOrderStatus(String status) {
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("ORDER_STATUS_INVALID", "订单状态不支持取消操作");
        }
    }

    private boolean canAdminCancel(OrderStatus status) {
        return ADMIN_CANCELLABLE_STATUSES.contains(status);
    }

    private Instant readAddressDeliveryTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Instant deliveryTime = readInstantText(text);
        if (deliveryTime == null) {
            throw new BusinessException("DELIVERY_TIME_INVALID", "送货时间格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
        return deliveryTime;
    }

    private Integer readInteger(JsonNode node, String... names) {
        String text = readText(node, names);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.trim()).intValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal readDecimal(JsonNode node, String... names) {
        String text = readText(node, names);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Instant readInstant(JsonNode node, String... names) {
        String text = readText(node, names);
        return readInstantText(text);
    }

    private Instant readInstantText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // 兼容老后台时间格式。
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
            // 兼容老后台时间格式。
        }
        try {
            return LocalDateTime.parse(trimmed, LEGACY_DATE_TIME_FORMATTER)
                    .atZone(DEFAULT_PAYLOAD_ZONE)
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            // 兼容老后台只传日期的地址修改表单。
        }
        try {
            return LocalDate.parse(trimmed)
                    .atStartOfDay(DEFAULT_PAYLOAD_ZONE)
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON_WRITE_FAILED", "JSON 序列化失败");
        }
    }
}
