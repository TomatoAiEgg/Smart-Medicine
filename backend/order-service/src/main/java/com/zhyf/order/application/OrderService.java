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
    private static final int ADMIN_ORDER_WAREHOUSE_EXPORT_LIMIT = 5000;
    private static final Set<OrderStatus> ADMIN_CANCELLABLE_STATUSES = EnumSet.of(
            OrderStatus.CREATED,
            OrderStatus.AUDIT_PASSED,
            OrderStatus.RECHECKED
    );
    private static final Set<OrderStatus> ADMIN_PRESCRIPTION_EDITABLE_STATUSES = EnumSet.of(
            OrderStatus.CREATED,
            OrderStatus.AUDIT_PASSED
    );
    private static final Set<OrderStatus> ADMIN_RECEIPTABLE_STATUSES = EnumSet.of(
            OrderStatus.RECHECKED,
            OrderStatus.DECOCTING,
            OrderStatus.DECOCTED,
            OrderStatus.PACKED,
            OrderStatus.SHIPPED,
            OrderStatus.IN_TRANSIT
    );
    private static final Set<OrderStatus> ADMIN_REPRINTABLE_STATUSES = EnumSet.of(
            OrderStatus.AUDIT_PASSED,
            OrderStatus.RECHECKED,
            OrderStatus.DECOCTING,
            OrderStatus.DECOCTED,
            OrderStatus.PACKED,
            OrderStatus.SHIPPED,
            OrderStatus.IN_TRANSIT,
            OrderStatus.SIGNED
    );
    private static final String ORDER_INITIALIZE_EVENT_TYPE = "ORDER_PRESCRIPTION_UPDATED";
    private static final String MANUAL_PROCESS_SOURCE = "admin-manual-process";
    private static final String MANUAL_PROCESS_LOGISTICS_COMPANY = "默认物流";
    private static final String MANUAL_PROCESS_DEVICE = "MANUAL-PROCESS";
    private static final UUID DEFAULT_ADMIN_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

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

    public AdminOperatorPage listAdminOperators(AdminOperatorQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminOperators(new AdminOperatorQuery(
                cleanText(query.keyword()),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminOperatorRecord createAdminOperator(AdminOperatorCommand command) {
        String username = requireText(command.username(), "OPERATOR_USERNAME_REQUIRED", "Operator username is required");
        String displayName = requireText(command.displayName(), "OPERATOR_DISPLAY_NAME_REQUIRED", "Operator display name is required");
        if (orderRepository.findAdminOperatorByUsername(DEFAULT_ADMIN_TENANT_ID, username).isPresent()) {
            throw new BusinessException("OPERATOR_USERNAME_DUPLICATED", "Operator username already exists");
        }
        return orderRepository.insertAdminOperator(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                username,
                displayName,
                cleanText(command.roleCode()),
                command.enabled() == null || command.enabled()
        );
    }

    @Transactional
    public AdminOperatorRecord updateAdminOperator(UUID operatorId, AdminOperatorCommand command) {
        AdminOperatorRecord existing = orderRepository.findAdminOperatorById(operatorId)
                .orElseThrow(() -> new BusinessException("OPERATOR_NOT_FOUND", "Operator not found"));
        String displayName = requireText(command.displayName(), "OPERATOR_DISPLAY_NAME_REQUIRED", "Operator display name is required");
        return orderRepository.updateAdminOperator(
                existing.id(),
                displayName,
                cleanText(command.roleCode()),
                command.enabled() == null || command.enabled()
        );
    }

    public AdminInstitutionPage listAdminInstitutions(AdminInstitutionQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminInstitutions(new AdminInstitutionQuery(
                cleanText(query.keyword()),
                cleanText(query.status()),
                cleanText(query.institutionType()),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminInstitutionRecord createAdminInstitution(AdminInstitutionCommand command) {
        String institutionCode = requireText(
                command.institutionCode(),
                "INSTITUTION_CODE_REQUIRED",
                "Institution code is required"
        );
        String institutionName = requireText(
                command.institutionName(),
                "INSTITUTION_NAME_REQUIRED",
                "Institution name is required"
        );
        if (orderRepository.findAdminInstitutionByCode(DEFAULT_ADMIN_TENANT_ID, institutionCode).isPresent()) {
            throw new BusinessException("INSTITUTION_CODE_DUPLICATED", "Institution code already exists");
        }
        return orderRepository.insertAdminInstitution(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                institutionCode,
                institutionName,
                defaultText(command.institutionType(), "HOSPITAL"),
                defaultText(command.status(), "ENABLED"),
                cleanText(command.storageType())
        );
    }

    @Transactional
    public AdminInstitutionRecord updateAdminInstitution(UUID institutionId, AdminInstitutionCommand command) {
        AdminInstitutionRecord existing = orderRepository.findAdminInstitutionById(institutionId)
                .orElseThrow(() -> new BusinessException("INSTITUTION_NOT_FOUND", "Institution not found"));
        String institutionName = requireText(
                command.institutionName(),
                "INSTITUTION_NAME_REQUIRED",
                "Institution name is required"
        );
        return orderRepository.updateAdminInstitution(
                existing.id(),
                institutionName,
                defaultText(command.institutionType(), existing.institutionType()),
                defaultText(command.status(), existing.status()),
                cleanText(command.storageType())
        );
    }

    public AdminInstitutionIpWhitelistPage listAdminInstitutionIpWhitelists(
            AdminInstitutionIpWhitelistQuery query
    ) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminInstitutionIpWhitelists(new AdminInstitutionIpWhitelistQuery(
                cleanText(query.keyword()),
                query.institutionId(),
                cleanText(query.ipRange()),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminInstitutionIpWhitelistRecord createAdminInstitutionIpWhitelist(
            AdminInstitutionIpWhitelistCommand command
    ) {
        UUID institutionId = command.institutionId();
        if (institutionId == null) {
            throw new BusinessException("INSTITUTION_ID_REQUIRED", "Institution is required");
        }
        AdminInstitutionRecord institution = orderRepository.findAdminInstitutionById(institutionId)
                .orElseThrow(() -> new BusinessException("INSTITUTION_NOT_FOUND", "Institution not found"));
        String ipRange = requireText(command.ipRange(), "IP_RANGE_REQUIRED", "IP range is required");
        if (orderRepository.findAdminInstitutionIpWhitelistByInstitutionAndRange(institution.id(), ipRange).isPresent()) {
            throw new BusinessException("IP_RANGE_DUPLICATED", "IP range already exists for institution");
        }
        return orderRepository.insertAdminInstitutionIpWhitelist(
                UUID.randomUUID(),
                institution.tenantId(),
                institution.id(),
                ipRange,
                command.enabled() == null || command.enabled()
        );
    }

    @Transactional
    public AdminInstitutionIpWhitelistRecord updateAdminInstitutionIpWhitelist(
            UUID whitelistId,
            AdminInstitutionIpWhitelistCommand command
    ) {
        AdminInstitutionIpWhitelistRecord existing = orderRepository.findAdminInstitutionIpWhitelistById(whitelistId)
                .orElseThrow(() -> new BusinessException("IP_WHITELIST_NOT_FOUND", "IP whitelist not found"));
        String ipRange = requireText(command.ipRange(), "IP_RANGE_REQUIRED", "IP range is required");
        orderRepository.findAdminInstitutionIpWhitelistByInstitutionAndRange(existing.institutionId(), ipRange)
                .filter(duplicated -> !duplicated.id().equals(existing.id()))
                .ifPresent(duplicated -> {
                    throw new BusinessException("IP_RANGE_DUPLICATED", "IP range already exists for institution");
                });
        return orderRepository.updateAdminInstitutionIpWhitelist(
                existing.id(),
                ipRange,
                command.enabled() == null ? existing.enabled() : command.enabled()
        );
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
                query.excludeOrderStatus(),
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

    public AdminManualProcessPage listAdminManualProcessOrders(AdminManualProcessQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        AdminManualProcessQuery normalized = new AdminManualProcessQuery(
                query.startTime(),
                query.endTime(),
                query.institution(),
                query.prescriptionType(),
                query.hospitalType(),
                query.isWithin(),
                defaultText(query.processType(), "PENDING"),
                query.deliveryType(),
                query.orderNo(),
                query.prescriptionNo(),
                query.hospitalPrescriptionNo(),
                query.patientName(),
                cleanText(query.doseRange()),
                page,
                pageSize
        );
        return orderRepository.searchAdminManualProcessOrders(normalized);
    }

    public AdminOrderWarehousePage listAdminOrderWarehouses(AdminOrderWarehouseQuery query) {
        AdminOrderWarehouseQuery normalized = normalizeOrderWarehouseQuery(query, false);
        return orderRepository.searchAdminOrderWarehouses(normalized);
    }

    public String exportAdminOrderWarehousesCsv(AdminOrderWarehouseQuery query) {
        AdminOrderWarehouseQuery normalized = normalizeOrderWarehouseQuery(query, true);
        List<AdminOrderWarehouseItem> rows = orderRepository.exportAdminOrderWarehouses(
                normalized,
                ADMIN_ORDER_WAREHOUSE_EXPORT_LIMIT
        );
        StringBuilder builder = new StringBuilder();
        builder.append('\ufeff');
        appendCsvRow(builder, List.of(
                "订单号",
                "订单状态",
                "接单时间",
                "批次",
                "医疗机构",
                "送货方式",
                "收货人",
                "收货电话",
                "收货时间",
                "收货地址",
                "门诊住院",
                "病人姓名",
                "病人年龄",
                "科室",
                "处方类型",
                "剂数",
                "包数",
                "每包剂量"
        ));
        for (AdminOrderWarehouseItem row : rows) {
            appendCsvRow(builder, List.of(
                    value(row.orderNo()),
                    orderStatusText(row.orderStatus()),
                    dateTime(row.createdAt()),
                    batchText(row.batchNo()),
                    value(row.institutionName()),
                    deliveryTypeText(row.addressType()),
                    value(row.receiverName()),
                    value(row.receiverPhone()),
                    dateTime(row.deliveryTime()),
                    warehouseAddress(row),
                    hospitalTypeText(row.hospitalTypes()),
                    value(row.patientName()),
                    value(row.patientAge()),
                    value(row.departmentNames()),
                    prescriptionTypeText(row.prescriptionTypes()),
                    value(row.doseCounts()),
                    value(row.perPackNums()),
                    value(row.perPackDoses())
            ));
        }
        return builder.toString();
    }

    public String exportAdminOrdersCsv(AdminOrderSearchQuery query) {
        AdminOrderSearchQuery normalized = new AdminOrderSearchQuery(
                query.startTime(),
                query.endTime(),
                query.institution(),
                query.prescriptionType(),
                query.hospitalType(),
                query.orderStatus(),
                query.excludeOrderStatus(),
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

    public AdminOrderReceiptPage listAdminOrderReceipts(AdminOrderReceiptQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        AdminOrderReceiptQuery normalized = new AdminOrderReceiptQuery(
                query.prescriptionNo(),
                query.receiverName(),
                query.receiverPhone(),
                query.patientName(),
                page,
                pageSize
        );
        return orderRepository.searchAdminOrderReceipts(normalized, receiptableStatusNames());
    }

    public AdminPrescriptionReprintPage listAdminPrescriptionReprints(AdminPrescriptionReprintQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        AdminPrescriptionReprintQuery normalized = new AdminPrescriptionReprintQuery(
                query.startTime(),
                query.endTime(),
                query.prescriptionNo(),
                page,
                pageSize
        );
        return orderRepository.searchAdminPrescriptionReprints(normalized, reprintableStatusNames());
    }

    public AdminPrescriptionPrintPayload getAdminPrescriptionPrintPayload(String prescriptionNo) {
        String normalizedPrescriptionNo = requireText(
                prescriptionNo,
                "PRESCRIPTION_NO_REQUIRED",
                "处方号不能为空"
        );
        String orderNo = orderRepository.findOrderNoByPrescriptionNo(normalizedPrescriptionNo)
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
        AdminOrderDetail detail = getAdminOrderDetail(orderNo);
        AdminOrderDetail.Prescription prescription = detail.prescriptions().stream()
                .filter(item -> normalizedPrescriptionNo.equals(item.prescriptionNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
        return new AdminPrescriptionPrintPayload(
                detail.orderId(),
                prescription.prescriptionId(),
                detail.orderNo(),
                detail.externalOrderNo(),
                detail.orderStatus(),
                detail.institutionName(),
                detail.patientName(),
                detail.patientPhone(),
                detail.receiverName(),
                detail.receiverPhone(),
                detail.receiverProvince(),
                detail.receiverCity(),
                detail.receiverZone(),
                detail.receiverAddress(),
                detail.addressType(),
                detail.deliveryTime(),
                detail.batchNo(),
                prescription.prescriptionNo(),
                prescription.externalPrescriptionNo(),
                prescription.prescriptionType(),
                prescription.prescriptionStatus(),
                prescription.hospitalType(),
                prescription.doseCount(),
                prescription.decoctionCount(),
                prescription.boilTimes(),
                prescription.isWithin(),
                prescription.perPackNum(),
                prescription.perPackDose(),
                prescription.totalAmount(),
                prescription.doctorName(),
                prescription.diagnosis(),
                prescription.departmentName(),
                prescription.wardName(),
                prescription.bedNo(),
                prescription.medicationMethod(),
                prescription.medicationInstruction(),
                prescription.prescriptionRemark(),
                prescription.details(),
                Instant.now()
        );
    }

    @Transactional
    public AdminManualProcessResult manualProcessAdminOrder(String orderNo, AdminManualProcessCommand command) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        AdminOrderDetail current = getAdminOrderDetail(orderNo.trim());
        OrderStatus currentStatus = parseOrderStatus(current.orderStatus());
        if (!OrderStatus.CREATED.equals(currentStatus)) {
            throw new BusinessException("ORDER_MANUAL_PROCESS_NOT_ALLOWED", "只有待审核订单允许走流程补录");
        }

        ManualProcessContext context = manualProcessContext(command);
        int updated = orderRepository.updateOrderStatusAndAppendRemarkIfCurrent(
                current.orderId(),
                OrderStatus.CREATED.name(),
                OrderStatus.SIGNED.name(),
                context.remark()
        );
        if (updated == 0) {
            throw new BusinessException("ORDER_MANUAL_PROCESS_CONFLICT", "订单状态已变更，请刷新后重试");
        }
        orderRepository.completeManualProcessPrescriptionStatuses(current.orderId());

        UUID reviewTaskId = UUID.randomUUID();
        UUID dispenseTaskId = UUID.randomUUID();
        UUID recheckTaskId = UUID.randomUUID();
        int workflowTaskCount = 0;
        workflowTaskCount += insertManualWorkflowTask(
                current,
                reviewTaskId,
                "ORDER_REVIEW",
                "APPROVED",
                context.auditor(),
                context.remark(),
                context.auditTime()
        );
        workflowTaskCount += insertManualWorkflowTask(
                current,
                dispenseTaskId,
                "PRESCRIPTION_DISPENSE",
                "COMPLETED",
                context.dispenser(),
                context.remark(),
                context.dispenseTime()
        );
        workflowTaskCount += insertManualWorkflowTask(
                current,
                recheckTaskId,
                "PRESCRIPTION_RECHECK",
                "COMPLETED",
                context.rechecker(),
                context.remark(),
                context.recheckTime()
        );

        orderRepository.insertPrescriptionAuditRecord(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                reviewTaskId,
                context.auditor(),
                context.remark(),
                context.auditTime()
        );
        int dispenseRecordCount = orderRepository.insertDispenseRecord(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                dispenseTaskId,
                context.dispenser(),
                context.remark(),
                context.dispenseTime()
        );
        orderRepository.insertPrescriptionRecheckRecord(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                recheckTaskId,
                context.rechecker(),
                context.remark(),
                context.recheckTime()
        );

        int decoctionTaskCount = insertManualDecoctionTasks(current, context);
        insertManualProcessStatusLogs(current, decoctionTaskCount > 0);
        String logisticsNo = "MANUAL-" + current.orderNo();
        orderRepository.upsertSignedShipment(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                current.orderNo(),
                logisticsNo,
                MANUAL_PROCESS_LOGISTICS_COMPANY,
                context.outboundTime(),
                context.outboundTime(),
                context.signTime()
        );
        String storedLogisticsNo = orderRepository.findShipmentNoByOrderId(current.orderId()).orElse(logisticsNo);
        orderRepository.insertShipmentTrace(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                storedLogisticsNo,
                "SHIPPED",
                "手工走流程补录出库",
                context.outboundTime()
        );
        orderRepository.insertShipmentTrace(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                storedLogisticsNo,
                "SIGNED",
                "手工走流程补录签收",
                context.signTime()
        );
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                null,
                context.operator(),
                "ORDER_MANUAL_PROCESS",
                "SUCCESS",
                context.remark(),
                writeJson(manualProcessPayload(current, context, storedLogisticsNo, true))
        );
        return new AdminManualProcessResult(
                current.orderId(),
                current.orderNo(),
                OrderStatus.CREATED.name(),
                OrderStatus.SIGNED.name(),
                workflowTaskCount,
                dispenseRecordCount,
                decoctionTaskCount,
                storedLogisticsNo,
                true,
                Instant.now()
        );
    }

    @Transactional
    public AdminOrderReceiptResult receiptAdminOrder(String orderNo, AdminOrderReceiptCommand command) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        return receiptAdminOrderInternal(orderNo.trim(), command);
    }

    @Transactional
    public AdminBatchOrderReceiptResult batchReceiptAdminOrders(AdminBatchOrderReceiptCommand command) {
        if (command == null || command.orderNos() == null || command.orderNos().isEmpty()) {
            throw new BusinessException("ORDER_RECEIPT_BATCH_REQUIRED", "请输入要签收的订单号");
        }
        List<String> orderNos = command.orderNos().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (orderNos.isEmpty()) {
            throw new BusinessException("ORDER_RECEIPT_BATCH_REQUIRED", "请输入要签收的订单号");
        }
        List<AdminOrderReceiptResult> results = new ArrayList<>();
        for (String orderNo : orderNos) {
            try {
                results.add(receiptAdminOrderInternal(orderNo, new AdminOrderReceiptCommand(
                        command.operator(),
                        command.reason()
                )));
            } catch (BusinessException ex) {
                results.add(new AdminOrderReceiptResult(
                        orderNo,
                        null,
                        OrderStatus.SIGNED.name(),
                        false,
                        ex.getMessage(),
                        Instant.now()
                ));
            }
        }
        int successCount = (int) results.stream().filter(AdminOrderReceiptResult::success).count();
        return new AdminBatchOrderReceiptResult(
                results.size(),
                successCount,
                results.size() - successCount,
                results
        );
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
        Integer boilTimes = requireNonNegative(command.boilTimes(), "BOIL_TIMES_INVALID", "几煎不能小于 0");
        Integer perPackNum = requireNonNegative(command.perPackNum(), "PER_PACK_NUM_INVALID", "每剂包数不能小于 0");
        Integer perPackDose = requireNonNegative(command.perPackDose(), "PER_PACK_DOSE_INVALID", "每剂剂量不能小于 0");
        Integer isWithin = validateIsWithin(command.isWithin());
        if (boilTimes != null && doseCount != null) {
            decoctionCount = boilTimes * doseCount;
        }
        if (isDecoctionPrescription(prescriptionType) && (boilTimes == null || boilTimes == 0)) {
            throw new BusinessException("BOIL_TIMES_REQUIRED", "代煎处方的几煎必须大于 0");
        }
        int updated = orderRepository.updatePrescription(
                current.orderId(),
                prescriptionId,
                prescriptionType,
                hospitalType,
                doseCount,
                decoctionCount,
                boilTimes,
                isWithin,
                perPackNum,
                perPackDose,
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
                writeJson(prescriptionUpdatePayload(oldPrescription, command, decoctionCount, boilTimes,
                        isWithin, perPackNum, perPackDose))
        );
        String eventPayload = """
                {"tenantId":"%s","orderId":"%s","orderNo":"%s","externalOrderNo":"%s","prescriptionIds":%s,"sourceAction":"ORDER_PRESCRIPTION_UPDATE"}
                """.formatted(
                current.tenantId(),
                current.orderId(),
                current.orderNo(),
                current.externalOrderNo(),
                writeJson(List.of(prescriptionId))
        );
        orderRepository.insertOutbox(
                UUID.randomUUID(),
                current.tenantId(),
                UUID.randomUUID().toString(),
                "ORDER_PRESCRIPTION_UPDATED",
                "ORDER",
                current.orderId().toString(),
                eventPayload
        );
        return getAdminOrderDetail(orderNo.trim()).prescriptions().stream()
                .filter(prescription -> prescription.prescriptionId().equals(prescriptionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
    }

    @Transactional
    public AdminPrescriptionActionResult initializeAdminPrescription(
            String orderNo,
            UUID prescriptionId,
            AdminPrescriptionActionCommand command
    ) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        if (prescriptionId == null) {
            throw new BusinessException("PRESCRIPTION_ID_REQUIRED", "处方 ID 不能为空");
        }
        AdminOrderDetail current = getAdminOrderDetail(orderNo.trim());
        AdminOrderDetail.Prescription prescription = findDetailPrescription(current, prescriptionId);
        OrderStatus currentOrderStatus = parseOrderStatus(current.orderStatus());
        PrescriptionStatus currentPrescriptionStatus = parsePrescriptionStatus(prescription.prescriptionStatus());
        if (OrderStatus.SIGNED.equals(currentOrderStatus)) {
            throw new BusinessException("PRESCRIPTION_INITIALIZE_NOT_ALLOWED", "已签收订单不允许初始化处方");
        }
        if (OrderStatus.CREATED.equals(currentOrderStatus) && PrescriptionStatus.CREATED.equals(currentPrescriptionStatus)) {
            throw new BusinessException("PRESCRIPTION_INITIALIZE_NOT_REQUIRED", "当前处方已经是初始状态");
        }
        String operator = command == null ? "admin" : defaultText(command.operator(), "admin");
        String reason = command == null ? "订单操作初始化处方" : defaultText(command.reason(), "订单操作初始化处方");
        int resetPrescription = orderRepository.updatePrescriptionStatus(
                current.orderId(),
                prescriptionId,
                PrescriptionStatus.CREATED.name()
        );
        boolean orderStatusChanged = !OrderStatus.CREATED.equals(currentOrderStatus);
        if (resetPrescription == 0 && !orderStatusChanged) {
            throw new BusinessException("PRESCRIPTION_INITIALIZE_CONFLICT", "处方状态已变更，请刷新后重试");
        }
        if (orderStatusChanged) {
            int updatedOrder = orderRepository.updateOrderStatusIfCurrent(
                    current.orderId(),
                    currentOrderStatus.name(),
                    OrderStatus.CREATED.name()
            );
            if (updatedOrder == 0) {
                throw new BusinessException("PRESCRIPTION_INITIALIZE_CONFLICT", "订单状态已变更，请刷新后重试");
            }
            orderRepository.cancelPendingWorkflowTasks(current.orderId(), operator, reason);
            orderRepository.cancelActiveDecoctionTasksByOrderId(current.orderId(), operator, reason);
            orderRepository.deleteShipmentRuntimeByOrderId(current.orderId());
            orderRepository.insertOrderStatusLog(
                    UUID.randomUUID(),
                    current.tenantId(),
                    current.orderId(),
                    currentOrderStatus.name(),
                    OrderStatus.CREATED.name(),
                    "ADMIN",
                    "admin-prescription-initialize"
            );
        }
        String eventId = UUID.randomUUID().toString();
        orderRepository.insertOutbox(
                UUID.randomUUID(),
                current.tenantId(),
                eventId,
                ORDER_INITIALIZE_EVENT_TYPE,
                "ORDER",
                current.orderId().toString(),
                writeJson(prescriptionActionPayload(current, prescriptionId, "PRESCRIPTION_INITIALIZE"))
        );
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                prescriptionId,
                operator,
                "PRESCRIPTION_INITIALIZE",
                resetPrescription > 0 || orderStatusChanged ? "SUCCESS" : "UNCHANGED",
                reason,
                writeJson(Map.of(
                        "fromPrescriptionStatus", currentPrescriptionStatus.name(),
                        "toPrescriptionStatus", PrescriptionStatus.CREATED.name(),
                        "fromOrderStatus", currentOrderStatus.name(),
                        "toOrderStatus", orderStatusChanged ? OrderStatus.CREATED.name() : currentOrderStatus.name(),
                        "eventId", eventId
                ))
        );
        return new AdminPrescriptionActionResult(
                current.orderId(),
                current.orderNo(),
                prescriptionId,
                prescription.prescriptionNo(),
                currentPrescriptionStatus.name(),
                PrescriptionStatus.CREATED.name(),
                orderStatusChanged,
                currentOrderStatus.name(),
                orderStatusChanged ? OrderStatus.CREATED.name() : currentOrderStatus.name(),
                eventId,
                Instant.now()
        );
    }

    @Transactional
    public AdminPrescriptionActionResult cancelAdminPrescription(
            String orderNo,
            UUID prescriptionId,
            AdminPrescriptionActionCommand command
    ) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        if (prescriptionId == null) {
            throw new BusinessException("PRESCRIPTION_ID_REQUIRED", "处方 ID 不能为空");
        }
        AdminOrderDetail current = getAdminOrderDetail(orderNo.trim());
        AdminOrderDetail.Prescription prescription = findDetailPrescription(current, prescriptionId);
        OrderStatus currentOrderStatus = parseOrderStatus(current.orderStatus());
        PrescriptionStatus currentPrescriptionStatus = parsePrescriptionStatus(prescription.prescriptionStatus());
        if (OrderStatus.SIGNED.equals(currentOrderStatus)
                || OrderStatus.CANCELLED.equals(currentOrderStatus)
                || OrderStatus.AUDIT_FAILED.equals(currentOrderStatus)) {
            throw new BusinessException("PRESCRIPTION_CANCEL_NOT_ALLOWED", "当前订单状态不允许取消处方");
        }
        if (PrescriptionStatus.CANCELLED.equals(currentPrescriptionStatus)) {
            throw new BusinessException("PRESCRIPTION_CANCEL_NOT_REQUIRED", "当前处方已经取消");
        }
        String operator = command == null ? "admin" : defaultText(command.operator(), "admin");
        String reason = command == null ? "订单操作取消处方" : defaultText(command.reason(), "订单操作取消处方");
        int cancelledPrescription = orderRepository.updatePrescriptionStatus(
                current.orderId(),
                prescriptionId,
                PrescriptionStatus.CANCELLED.name()
        );
        if (cancelledPrescription == 0) {
            throw new BusinessException("PRESCRIPTION_CANCEL_CONFLICT", "处方状态已变更，请刷新后重试");
        }
        int activePrescriptionCount = orderRepository.countActivePrescriptionsByOrderId(current.orderId());
        boolean orderStatusChanged = activePrescriptionCount == 0;
        if (orderStatusChanged) {
            int updatedOrder = orderRepository.updateOrderStatusIfCurrent(
                    current.orderId(),
                    currentOrderStatus.name(),
                    OrderStatus.CANCELLED.name()
            );
            if (updatedOrder == 0) {
                throw new BusinessException("PRESCRIPTION_CANCEL_CONFLICT", "订单状态已变更，请刷新后重试");
            }
            orderRepository.cancelPendingWorkflowTasks(current.orderId(), operator, reason);
            orderRepository.insertOrderStatusLog(
                    UUID.randomUUID(),
                    current.tenantId(),
                    current.orderId(),
                    currentOrderStatus.name(),
                    OrderStatus.CANCELLED.name(),
                    "ADMIN",
                    "admin-prescription-cancel"
            );
        }
        String eventId = UUID.randomUUID().toString();
        orderRepository.insertOutbox(
                UUID.randomUUID(),
                current.tenantId(),
                eventId,
                orderStatusChanged ? "ORDER_CANCELLED" : "PRESCRIPTION_CANCELLED",
                "ORDER",
                current.orderId().toString(),
                writeJson(prescriptionActionPayload(current, prescriptionId, "PRESCRIPTION_CANCEL"))
        );
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                prescriptionId,
                operator,
                "PRESCRIPTION_CANCEL",
                "SUCCESS",
                reason,
                writeJson(Map.of(
                        "fromPrescriptionStatus", currentPrescriptionStatus.name(),
                        "toPrescriptionStatus", PrescriptionStatus.CANCELLED.name(),
                        "fromOrderStatus", currentOrderStatus.name(),
                        "toOrderStatus", orderStatusChanged ? OrderStatus.CANCELLED.name() : currentOrderStatus.name(),
                        "activePrescriptionCount", activePrescriptionCount,
                        "eventId", eventId
                ))
        );
        return new AdminPrescriptionActionResult(
                current.orderId(),
                current.orderNo(),
                prescriptionId,
                prescription.prescriptionNo(),
                currentPrescriptionStatus.name(),
                PrescriptionStatus.CANCELLED.name(),
                orderStatusChanged,
                currentOrderStatus.name(),
                orderStatusChanged ? OrderStatus.CANCELLED.name() : currentOrderStatus.name(),
                eventId,
                Instant.now()
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

    @Transactional
    public AdminOrderInitializeResult initializeAdminOrder(String orderNo, AdminOrderInitializeCommand command) {
        if (command == null) {
            throw new BusinessException("ORDER_INITIALIZE_COMMAND_REQUIRED", "订单初始化参数不能为空");
        }
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        String normalizedOrderNo = orderNo.trim();
        AdminOrderDetail current = getAdminOrderDetail(normalizedOrderNo);
        OrderStatus currentStatus = parseOrderStatus(current.orderStatus());
        if (OrderStatus.CREATED.equals(currentStatus)) {
            throw new BusinessException("ORDER_INITIALIZE_NOT_REQUIRED", "当前订单已经是初始状态");
        }
        String operator = defaultText(command.operator(), "admin");
        String reason = defaultText(command.reason(), "订单中心手工初始化");

        int updated = orderRepository.updateOrderStatusIfCurrent(
                current.orderId(),
                currentStatus.name(),
                OrderStatus.CREATED.name()
        );
        if (updated == 0) {
            throw new BusinessException("ORDER_INITIALIZE_CONFLICT", "订单状态已变更，请刷新后重试");
        }
        int resetPrescriptions = orderRepository.updatePrescriptionsStatusByOrderId(
                current.orderId(),
                PrescriptionStatus.CREATED.name()
        );
        int cancelledWorkflowTasks = orderRepository.cancelPendingWorkflowTasks(current.orderId(), operator, reason);
        int cancelledDecoctionTasks = orderRepository.cancelActiveDecoctionTasksByOrderId(
                current.orderId(),
                operator,
                reason
        );
        int deletedShipments = orderRepository.deleteShipmentRuntimeByOrderId(current.orderId());

        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                currentStatus.name(),
                OrderStatus.CREATED.name(),
                "ADMIN",
                "admin-order-initialize"
        );
        List<UUID> prescriptionIds = orderRepository.findPrescriptionIdsByOrderId(current.orderId());
        String eventId = UUID.randomUUID().toString();
        String eventPayload = """
                {"tenantId":"%s","orderId":"%s","orderNo":"%s","externalOrderNo":"%s","prescriptionIds":%s,"sourceAction":"ORDER_INITIALIZE"}
                """.formatted(
                current.tenantId(),
                current.orderId(),
                current.orderNo(),
                current.externalOrderNo(),
                writeJson(prescriptionIds)
        );
        orderRepository.insertOutbox(
                UUID.randomUUID(),
                current.tenantId(),
                eventId,
                ORDER_INITIALIZE_EVENT_TYPE,
                "ORDER",
                current.orderId().toString(),
                eventPayload
        );
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                null,
                operator,
                "ORDER_INITIALIZE",
                "SUCCESS",
                reason,
                writeJson(Map.of(
                        "fromStatus", currentStatus.name(),
                        "toStatus", OrderStatus.CREATED.name(),
                        "resetPrescriptionCount", resetPrescriptions,
                        "cancelledWorkflowTaskCount", cancelledWorkflowTasks,
                        "cancelledDecoctionTaskCount", cancelledDecoctionTasks,
                        "deletedShipmentCount", deletedShipments,
                        "eventId", eventId
                ))
        );
        return new AdminOrderInitializeResult(
                current.orderId(),
                current.orderNo(),
                currentStatus.name(),
                OrderStatus.CREATED.name(),
                resetPrescriptions,
                cancelledWorkflowTasks,
                cancelledDecoctionTasks,
                deletedShipments,
                eventId,
                Instant.now()
        );
    }

    private AdminOrderReceiptResult receiptAdminOrderInternal(String orderNo, AdminOrderReceiptCommand command) {
        AdminOrderDetail current = getAdminOrderDetail(orderNo);
        OrderStatus currentStatus = parseOrderStatus(current.orderStatus());
        if (!ADMIN_RECEIPTABLE_STATUSES.contains(currentStatus)) {
            throw new BusinessException("ORDER_RECEIPT_NOT_ALLOWED", "当前订单状态不允许签收");
        }
        String operator = command == null ? "admin" : defaultText(command.operator(), "admin");
        String reason = command == null ? null : cleanText(command.reason());
        int updated = orderRepository.updateOrderStatusIfCurrent(
                current.orderId(),
                currentStatus.name(),
                OrderStatus.SIGNED.name()
        );
        if (updated == 0) {
            throw new BusinessException("ORDER_RECEIPT_CONFLICT", "订单状态已变更，请刷新后重试");
        }
        orderRepository.updateLatestShipmentToSigned(current.orderId());
        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                currentStatus.name(),
                OrderStatus.SIGNED.name(),
                "ADMIN",
                "admin-order-receipt"
        );
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                null,
                operator,
                "ORDER_RECEIPT",
                "SUCCESS",
                reason,
                writeJson(Map.of(
                        "orderNo", current.orderNo(),
                        "fromStatus", currentStatus.name(),
                        "toStatus", OrderStatus.SIGNED.name(),
                        "reason", value(reason)
                ))
        );
        orderRepository.insertOutbox(
                UUID.randomUUID(),
                current.tenantId(),
                UUID.randomUUID().toString(),
                "ORDER_SIGNED",
                "ORDER",
                current.orderId().toString(),
                writeJson(Map.of(
                        "tenantId", current.tenantId(),
                        "orderId", current.orderId(),
                        "orderNo", current.orderNo(),
                        "externalOrderNo", current.externalOrderNo(),
                        "sourceAction", "ORDER_RECEIPT"
                ))
        );
        return new AdminOrderReceiptResult(
                current.orderNo(),
                currentStatus.name(),
                OrderStatus.SIGNED.name(),
                true,
                "SUCCESS",
                Instant.now()
        );
    }

    private AdminOrderWarehouseQuery normalizeOrderWarehouseQuery(
            AdminOrderWarehouseQuery query,
            boolean export
    ) {
        int page = export ? 1 : Math.max(query.page(), 1);
        int pageSize = export
                ? ADMIN_ORDER_WAREHOUSE_EXPORT_LIMIT
                : Math.min(Math.max(query.pageSize(), 1), 100);
        return new AdminOrderWarehouseQuery(
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
                query.orderNo(),
                query.prescriptionNo(),
                query.hospitalPrescriptionNo(),
                query.patientName(),
                query.receiverPhone(),
                query.nodeTime(),
                page,
                pageSize
        );
    }

    private ManualProcessContext manualProcessContext(AdminManualProcessCommand command) {
        Instant now = Instant.now();
        String operator = command == null ? "admin" : defaultText(command.operator(), "admin");
        Instant auditTime = command == null || command.auditTime() == null ? now : command.auditTime();
        Instant dispenseTime = command == null || command.dispenseTime() == null
                ? auditTime.plusSeconds(5 * 60L)
                : command.dispenseTime();
        Instant recheckTime = command == null || command.recheckTime() == null
                ? auditTime.plusSeconds(30 * 60L)
                : command.recheckTime();
        Instant soakTimeStart = command == null || command.soakTimeStart() == null
                ? auditTime.plusSeconds(60 * 60L)
                : command.soakTimeStart();
        Instant boilTimeStart = command == null || command.boilTimeStart() == null
                ? auditTime.plusSeconds(90 * 60L)
                : command.boilTimeStart();
        Instant outboundTime = command == null || command.outboundTime() == null
                ? auditTime.plusSeconds(150 * 60L)
                : command.outboundTime();
        Instant signTime = command == null || command.signTime() == null
                ? auditTime.plusSeconds(24 * 60 * 60L)
                : command.signTime();
        return new ManualProcessContext(
                operator,
                command == null ? operator : defaultText(command.auditor(), operator),
                command == null ? operator : defaultText(command.dispenser(), operator),
                command == null ? operator : defaultText(command.rechecker(), operator),
                command == null ? "MANUAL" : defaultText(command.pailNo(), "MANUAL"),
                cleanText(command == null ? null : command.remark()),
                auditTime,
                dispenseTime,
                recheckTime,
                soakTimeStart,
                boilTimeStart,
                outboundTime,
                signTime
        );
    }

    private int insertManualWorkflowTask(
            AdminOrderDetail current,
            UUID taskId,
            String taskType,
            String taskStatus,
            String assignee,
            String comment,
            Instant completedAt
    ) {
        return orderRepository.insertCompletedWorkflowTask(
                taskId,
                current.tenantId(),
                current.orderId(),
                taskType,
                taskStatus,
                MANUAL_PROCESS_SOURCE + ":" + current.orderId() + ":" + taskType + ":" + taskId,
                assignee,
                comment,
                writeJson(Map.of(
                        "source", MANUAL_PROCESS_SOURCE,
                        "orderNo", current.orderNo(),
                        "externalOrderNo", value(current.externalOrderNo())
                )),
                completedAt
        );
    }

    private int insertManualDecoctionTasks(AdminOrderDetail current, ManualProcessContext context) {
        int count = 0;
        for (AdminOrderDetail.Prescription prescription : current.prescriptions()) {
            if (!isDecoctionPrescription(prescription.prescriptionType())) {
                continue;
            }
            UUID taskId = UUID.randomUUID();
            count += orderRepository.insertCompletedDecoctionTask(
                    taskId,
                    "MP" + taskId.toString().replace("-", "").substring(0, 18).toUpperCase(),
                    current.tenantId(),
                    current.orderId(),
                    prescription.prescriptionId(),
                    prescription.prescriptionNo(),
                    MANUAL_PROCESS_DEVICE,
                    context.pailNo(),
                    "MANUAL-BIND-" + taskId,
                    "MANUAL-START-" + taskId,
                    "MANUAL-FINISH-" + taskId,
                    context.operator(),
                    context.boilTimeStart(),
                    context.outboundTime()
            );
        }
        return count;
    }

    private void insertManualProcessStatusLogs(AdminOrderDetail current, boolean hasDecoctionTask) {
        insertManualStatusLog(current, OrderStatus.CREATED.name(), OrderStatus.AUDIT_PASSED.name(), "AUDIT");
        insertManualStatusLog(current, OrderStatus.AUDIT_PASSED.name(), OrderStatus.RECHECKED.name(), "RECHECK");
        if (hasDecoctionTask) {
            insertManualStatusLog(current, OrderStatus.RECHECKED.name(), OrderStatus.DECOCTING.name(), "DECOCTION");
            insertManualStatusLog(current, OrderStatus.DECOCTING.name(), OrderStatus.DECOCTED.name(), "DECOCTION");
            insertManualStatusLog(current, OrderStatus.DECOCTED.name(), OrderStatus.PACKED.name(), "LOGISTICS");
        } else {
            insertManualStatusLog(current, OrderStatus.RECHECKED.name(), OrderStatus.PACKED.name(), "LOGISTICS");
        }
        insertManualStatusLog(current, OrderStatus.PACKED.name(), OrderStatus.SHIPPED.name(), "LOGISTICS");
        insertManualStatusLog(current, OrderStatus.SHIPPED.name(), OrderStatus.SIGNED.name(), "ADMIN");
    }

    private void insertManualStatusLog(AdminOrderDetail current, String fromStatus, String toStatus, String operatorType) {
        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                fromStatus,
                toStatus,
                operatorType,
                MANUAL_PROCESS_SOURCE
        );
    }

    private Map<String, Object> manualProcessPayload(
            AdminOrderDetail current,
            ManualProcessContext context,
            String logisticsNo,
            boolean callbackSuppressed
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", current.orderNo());
        payload.put("externalOrderNo", current.externalOrderNo());
        payload.put("auditor", context.auditor());
        payload.put("auditTime", context.auditTime());
        payload.put("dispenser", context.dispenser());
        payload.put("dispenseTime", context.dispenseTime());
        payload.put("rechecker", context.rechecker());
        payload.put("recheckTime", context.recheckTime());
        payload.put("pailNo", context.pailNo());
        payload.put("soakTimeStart", context.soakTimeStart());
        payload.put("boilTimeStart", context.boilTimeStart());
        payload.put("outboundTime", context.outboundTime());
        payload.put("signTime", context.signTime());
        payload.put("logisticsNo", logisticsNo);
        payload.put("callbackSuppressed", callbackSuppressed);
        return payload;
    }

    private List<String> receiptableStatusNames() {
        return ADMIN_RECEIPTABLE_STATUSES.stream().map(OrderStatus::name).toList();
    }

    private List<String> reprintableStatusNames() {
        return ADMIN_REPRINTABLE_STATUSES.stream().map(OrderStatus::name).toList();
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
                readInteger(node, "boilTimes", "boil_times"),
                readInteger(node, "isWithin", "is_within", "recipeUsage"),
                readInteger(node, "perPackNum", "per_pack_num"),
                readInteger(node, "perPackDose", "per_pack_dose"),
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

    private PrescriptionStatus parsePrescriptionStatus(String status) {
        try {
            return PrescriptionStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("PRESCRIPTION_STATUS_INVALID", "处方状态不支持当前操作");
        }
    }

    private AdminOrderDetail.Prescription findDetailPrescription(AdminOrderDetail current, UUID prescriptionId) {
        return current.prescriptions().stream()
                .filter(prescription -> prescription.prescriptionId().equals(prescriptionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
    }

    private Map<String, Object> prescriptionActionPayload(
            AdminOrderDetail current,
            UUID prescriptionId,
            String sourceAction
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", current.tenantId());
        payload.put("orderId", current.orderId());
        payload.put("orderNo", current.orderNo());
        payload.put("externalOrderNo", current.externalOrderNo());
        payload.put("prescriptionIds", List.of(prescriptionId));
        payload.put("sourceAction", sourceAction);
        return payload;
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

    private Integer validateIsWithin(Integer value) {
        if (value != null && value != 0 && value != 1) {
            throw new BusinessException("IS_WITHIN_INVALID", "服用方式只能是内服或外用");
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
            AdminPrescriptionUpdateCommand command,
            Integer decoctionCount,
            Integer boilTimes,
            Integer isWithin,
            Integer perPackNum,
            Integer perPackDose
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("old", prescriptionSnapshot(oldPrescription));
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("prescriptionType", command.prescriptionType());
        next.put("hospitalType", command.hospitalType());
        next.put("doseCount", command.doseCount());
        next.put("decoctionCount", decoctionCount);
        next.put("boilTimes", boilTimes);
        next.put("isWithin", isWithin);
        next.put("perPackNum", perPackNum);
        next.put("perPackDose", perPackDose);
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
        snapshot.put("boilTimes", prescription.boilTimes());
        snapshot.put("isWithin", prescription.isWithin());
        snapshot.put("perPackNum", prescription.perPackNum());
        snapshot.put("perPackDose", prescription.perPackDose());
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

    private String warehouseAddress(AdminOrderWarehouseItem row) {
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
    private record ManualProcessContext(
            String operator,
            String auditor,
            String dispenser,
            String rechecker,
            String pailNo,
            String remark,
            Instant auditTime,
            Instant dispenseTime,
            Instant recheckTime,
            Instant soakTimeStart,
            Instant boilTimeStart,
            Instant outboundTime,
            Instant signTime
    ) {
    }
}
