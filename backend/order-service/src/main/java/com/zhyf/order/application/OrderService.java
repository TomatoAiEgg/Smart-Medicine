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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final DateTimeFormatter EXPORT_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(DEFAULT_PAYLOAD_ZONE);
    private static final int ADMIN_ORDER_EXPORT_LIMIT = 5000;
    private static final Set<OrderStatus> ADMIN_CANCELLABLE_STATUSES = EnumSet.of(
            OrderStatus.CREATED,
            OrderStatus.AUDIT_PASSED,
            OrderStatus.RECHECKED
    );
    private static final Set<OrderStatus> ADMIN_PRESCRIPTION_EDITABLE_STATUSES = EnumSet.of(
            OrderStatus.CREATED,
            OrderStatus.AUDIT_PASSED
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

    public String exportAdminOrdersCsv(AdminOrderSearchQuery query) {
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
                1,
                ADMIN_ORDER_EXPORT_LIMIT
        );
        List<AdminOrderListItem> rows = orderRepository.exportAdminOrders(normalized, ADMIN_ORDER_EXPORT_LIMIT);
        StringBuilder builder = new StringBuilder();
        builder.append('\ufeff');
        appendCsvRow(builder, List.of(
                "平台处方号",
                "平台订单号",
                "订单时间",
                "煎煮中心",
                "机构名称",
                "机构处方号",
                "病人姓名",
                "门诊住院",
                "处方类型",
                "剂数",
                "处方金额",
                "收货人",
                "收货电话",
                "收货信息",
                "收货时间",
                "送货方式",
                "订单状态",
                "批次",
                "物流公司",
                "物流单号",
                "物流状态",
                "订单备注"
        ));
        for (AdminOrderListItem row : rows) {
            appendCsvRow(builder, List.of(
                    value(row.prescriptionNos()),
                    value(row.orderNo()),
                    dateTime(row.createdAt()),
                    value(row.storageType()),
                    value(row.institutionName()),
                    value(row.externalPrescriptionNos()),
                    value(row.patientName()),
                    hospitalTypeText(row.hospitalTypes()),
                    prescriptionTypeText(row.prescriptionTypes()),
                    value(row.doseCount()),
                    value(row.totalAmount()),
                    value(row.receiverName()),
                    value(row.receiverPhone()),
                    receiverAddress(row),
                    dateTime(row.deliveryTime()),
                    deliveryTypeText(row.addressType()),
                    orderStatusText(row.orderStatus()),
                    batchText(row.batchNo()),
                    value(row.logisticsCompany()),
                    value(row.logisticsNo()),
                    orderStatusText(row.logisticsStatus()),
                    value(row.orderRemark())
            ));
        }
        return builder.toString();
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
    public AdminOrderDetail.Prescription updateAdminPrescription(
            String orderNo,
            UUID prescriptionId,
            AdminPrescriptionUpdateCommand command
    ) {
        if (command == null) {
            throw new BusinessException("PRESCRIPTION_UPDATE_COMMAND_REQUIRED", "处方修改参数不能为空");
        }
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        if (prescriptionId == null) {
            throw new BusinessException("PRESCRIPTION_ID_REQUIRED", "处方 ID 不能为空");
        }
        AdminOrderDetail current = getAdminOrderDetail(orderNo.trim());
        OrderStatus currentStatus = parseOrderStatus(current.orderStatus());
        if (!ADMIN_PRESCRIPTION_EDITABLE_STATUSES.contains(currentStatus)) {
            throw new BusinessException("PRESCRIPTION_UPDATE_NOT_ALLOWED", "当前订单状态不允许修改处方");
        }
        AdminOrderDetail.Prescription oldPrescription = current.prescriptions().stream()
                .filter(prescription -> prescription.prescriptionId().equals(prescriptionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
        if (PrescriptionStatus.CANCELLED.name().equals(oldPrescription.prescriptionStatus())) {
            throw new BusinessException("PRESCRIPTION_UPDATE_NOT_ALLOWED", "已取消处方不允许修改");
        }

        String prescriptionType = requireText(command.prescriptionType(), "PRESCRIPTION_TYPE_REQUIRED", "处方类型不能为空");
        String hospitalType = cleanText(command.hospitalType());
        Integer doseCount = requireNonNegative(command.doseCount(), "DOSE_COUNT_INVALID", "剂数不能小于 0");
        Integer decoctionCount = requireNonNegative(
                command.decoctionCount(),
                "DECOCTION_COUNT_INVALID",
                "煎煮剂数不能小于 0"
        );
        if (isDecoctionPrescription(prescriptionType) && (decoctionCount == null || decoctionCount == 0)) {
            throw new BusinessException("DECOCTION_COUNT_REQUIRED", "代煎处方的煎煮剂数必须大于 0");
        }
        int updated = orderRepository.updatePrescription(
                current.orderId(),
                prescriptionId,
                prescriptionType,
                hospitalType,
                doseCount,
                decoctionCount,
                cleanText(command.medicationMethod()),
                cleanText(command.medicationInstruction()),
                cleanText(command.prescriptionRemark())
        );
        if (updated == 0) {
            throw new BusinessException("PRESCRIPTION_UPDATE_FAILED", "处方修改失败");
        }
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                prescriptionId,
                defaultText(command.operator(), "admin"),
                "ORDER_PRESCRIPTION_UPDATE",
                "SUCCESS",
                cleanText(command.reason()),
                writeJson(prescriptionUpdatePayload(oldPrescription, command))
        );
        return getAdminOrderDetail(orderNo.trim()).prescriptions().stream()
                .filter(prescription -> prescription.prescriptionId().equals(prescriptionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
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

    private Integer requireNonNegative(Integer value, String code, String message) {
        if (value != null && value < 0) {
            throw new BusinessException(code, message);
        }
        return value;
    }

    private boolean isDecoctionPrescription(String prescriptionType) {
        return "2".equals(prescriptionType)
                || "DECOCTION".equals(prescriptionType)
                || "代煎".equals(prescriptionType);
    }

    private Map<String, Object> prescriptionUpdatePayload(
            AdminOrderDetail.Prescription oldPrescription,
            AdminPrescriptionUpdateCommand command
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("old", prescriptionSnapshot(oldPrescription));
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("prescriptionType", command.prescriptionType());
        next.put("hospitalType", command.hospitalType());
        next.put("doseCount", command.doseCount());
        next.put("decoctionCount", command.decoctionCount());
        next.put("medicationMethod", command.medicationMethod());
        next.put("medicationInstruction", command.medicationInstruction());
        next.put("prescriptionRemark", command.prescriptionRemark());
        payload.put("new", next);
        return payload;
    }

    private Map<String, Object> prescriptionSnapshot(AdminOrderDetail.Prescription prescription) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("prescriptionType", prescription.prescriptionType());
        snapshot.put("hospitalType", prescription.hospitalType());
        snapshot.put("doseCount", prescription.doseCount());
        snapshot.put("decoctionCount", prescription.decoctionCount());
        snapshot.put("medicationMethod", prescription.medicationMethod());
        snapshot.put("medicationInstruction", prescription.medicationInstruction());
        snapshot.put("prescriptionRemark", prescription.prescriptionRemark());
        return snapshot;
    }

    private void appendCsvRow(StringBuilder builder, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(csvCell(values.get(i)));
        }
        builder.append('\n');
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.startsWith("=") || escaped.startsWith("+") || escaped.startsWith("-") || escaped.startsWith("@")
                || escaped.startsWith("\t")) {
            escaped = "'" + escaped;
        }
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String dateTime(Instant value) {
        return value == null ? "" : EXPORT_DATE_TIME_FORMATTER.format(value);
    }

    private String receiverAddress(AdminOrderListItem row) {
        return List.of(
                        value(row.receiverProvince()),
                        value(row.receiverCity()),
                        value(row.receiverZone()),
                        value(row.receiverAddress())
                ).stream()
                .filter(StringUtils::hasText)
                .reduce("", String::concat);
    }

    private String hospitalTypeText(String value) {
        return switch (value(value)) {
            case "1", "OUTPATIENT", "门诊" -> "门诊";
            case "2", "INPATIENT", "住院" -> "住院";
            case "3", "OTHER", "其他" -> "其他";
            default -> value(value);
        };
    }

    private String prescriptionTypeText(String value) {
        return switch (value(value)) {
            case "DECOCTION", "代煎" -> "代煎";
            case "SELF_DECOCTION", "自煎" -> "自煎";
            default -> value(value);
        };
    }

    private String deliveryTypeText(String value) {
        return switch (value(value)) {
            case "HOSPITAL", "送医院" -> "送医院";
            case "PATIENT", "送个人" -> "送个人";
            case "PICKUP", "自提" -> "自提";
            default -> value(value);
        };
    }

    private String batchText(String value) {
        return switch (value(value)) {
            case "1", "MORNING", "早批次" -> "早批次";
            case "2", "NOON", "午批次" -> "午批次";
            case "3", "EVENING", "晚批次" -> "晚批次";
            default -> value(value);
        };
    }

    private String orderStatusText(String value) {
        return switch (value(value)) {
            case "CREATED" -> "已创建";
            case "AUDIT_PASSED" -> "审核通过";
            case "AUDIT_FAILED" -> "审核失败";
            case "RECHECKED" -> "已复核";
            case "DECOCTING" -> "煎煮中";
            case "DECOCTED" -> "已煎煮";
            case "PACKED" -> "已打包";
            case "SHIPPED" -> "已发货";
            case "IN_TRANSIT" -> "运输中";
            case "SIGNED" -> "已签收";
            case "CANCELLED" -> "已取消";
            default -> value(value);
        };
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
