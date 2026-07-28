package com.zhyf.order.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.order.application.AdminInstitutionApiCommand;
import com.zhyf.order.application.AdminInstitutionApiPage;
import com.zhyf.order.application.AdminInstitutionApiPermissionCommand;
import com.zhyf.order.application.AdminInstitutionApiPermissionPage;
import com.zhyf.order.application.AdminInstitutionApiPermissionQuery;
import com.zhyf.order.application.AdminInstitutionApiPermissionRecord;
import com.zhyf.order.application.AdminInstitutionApiQuery;
import com.zhyf.order.application.AdminInstitutionApiRecord;
import com.zhyf.order.application.AdminInstitutionAppCommand;
import com.zhyf.order.application.AdminInstitutionAppPage;
import com.zhyf.order.application.AdminInstitutionAppQuery;
import com.zhyf.order.application.AdminInstitutionAppRecord;
import com.zhyf.order.application.AdminInstitutionCommand;
import com.zhyf.order.application.AdminInstitutionIpWhitelistCommand;
import com.zhyf.order.application.AdminInstitutionIpWhitelistPage;
import com.zhyf.order.application.AdminInstitutionIpWhitelistQuery;
import com.zhyf.order.application.AdminInstitutionIpWhitelistRecord;
import com.zhyf.order.application.AdminInstitutionPage;
import com.zhyf.order.application.AdminInstitutionQuery;
import com.zhyf.order.application.AdminInstitutionRecord;
import com.zhyf.order.application.AdminLogisticsSpecialRuleCommand;
import com.zhyf.order.application.AdminLogisticsSpecialRulePage;
import com.zhyf.order.application.AdminLogisticsSpecialRuleQuery;
import com.zhyf.order.application.AdminLogisticsSpecialRuleRecord;
import com.zhyf.order.application.AdminOrderAddressUpdateCommand;
import com.zhyf.order.application.AdminOrderAddressUpdateResult;
import com.zhyf.order.application.AdminOrderCancelCommand;
import com.zhyf.order.application.AdminOrderCancelResult;
import com.zhyf.order.application.AdminOrderDetail;
import com.zhyf.order.application.AdminOrderInitializeCommand;
import com.zhyf.order.application.AdminOrderInitializeResult;
import com.zhyf.order.application.AdminOrderPage;
import com.zhyf.order.application.AdminOrderReceiptCommand;
import com.zhyf.order.application.AdminOrderReceiptPage;
import com.zhyf.order.application.AdminOrderReceiptQuery;
import com.zhyf.order.application.AdminOrderReceiptResult;
import com.zhyf.order.application.AdminOrderWarehousePage;
import com.zhyf.order.application.AdminOrderWarehouseQuery;
import com.zhyf.order.application.AdminOrderSearchQuery;
import com.zhyf.order.application.AdminOperatorCommand;
import com.zhyf.order.application.AdminOperatorPage;
import com.zhyf.order.application.AdminOperatorQuery;
import com.zhyf.order.application.AdminOperatorRecord;
import com.zhyf.order.application.AdminBatchOrderReceiptCommand;
import com.zhyf.order.application.AdminBatchOrderReceiptResult;
import com.zhyf.order.application.AdminManualProcessCommand;
import com.zhyf.order.application.AdminManualProcessPage;
import com.zhyf.order.application.AdminManualProcessQuery;
import com.zhyf.order.application.AdminManualProcessResult;
import com.zhyf.order.application.AdminPrescriptionActionCommand;
import com.zhyf.order.application.AdminPrescriptionActionResult;
import com.zhyf.order.application.AdminPrescriptionPrintPayload;
import com.zhyf.order.application.AdminPrescriptionReprintPage;
import com.zhyf.order.application.AdminPrescriptionReprintQuery;
import com.zhyf.order.application.AdminPrescriptionUpdateCommand;
import com.zhyf.order.application.OrderCreateCommand;
import com.zhyf.order.application.OrderCreateResult;
import com.zhyf.order.application.OrderReviewCommand;
import com.zhyf.order.application.OrderReviewResult;
import com.zhyf.order.application.OrderReviewTaskService;
import com.zhyf.order.application.OrderService;
import com.zhyf.order.domain.OrderProgressSnapshot;
import com.zhyf.order.domain.WorkflowTaskSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InstitutionOrderController {

    private static final ZoneId DEFAULT_QUERY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LEGACY_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderService orderService;
    private final OrderReviewTaskService orderReviewTaskService;

    public InstitutionOrderController(OrderService orderService, OrderReviewTaskService orderReviewTaskService) {
        this.orderService = orderService;
        this.orderReviewTaskService = orderReviewTaskService;
    }

    @PostMapping("/institution/createOrder")
    public ApiResponse<OrderCreateResult> createOrder(
            @RequestHeader("X-App-Key") String appKey,
            @RequestHeader("X-Timestamp") String timestamp,
            @RequestHeader("X-Signature") String signature,
            @RequestBody JsonNode payload,
            HttpServletRequest request
    ) {
        OrderCreateCommand command = new OrderCreateCommand(
                appKey,
                timestamp,
                signature,
                request.getRemoteAddr(),
                payload
        );
        return ApiResponse.ok(orderService.createOrder(command));
    }

    @GetMapping("/admin/orders")
    public ApiResponse<AdminOrderPage> listOrders(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String prescriptionType,
            @RequestParam(required = false) String hospitalType,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String excludeOrderStatus,
            @RequestParam(required = false) String decoctionCenter,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String logisticsCompany,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String hospitalPrescriptionNo,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) String receiverPhone,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        AdminOrderSearchQuery query = new AdminOrderSearchQuery(
                parseQueryTime(startTime),
                parseQueryTime(endTime),
                institution,
                prescriptionType,
                hospitalType,
                orderStatus,
                excludeOrderStatus,
                decoctionCenter,
                deliveryType,
                logisticsCompany,
                province,
                keyword,
                hospitalPrescriptionNo,
                patientName,
                receiverPhone,
                page,
                pageSize
        );
        return ApiResponse.ok(orderService.listAdminOrders(query));
    }

    @GetMapping(value = "/admin/orders/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportOrders(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String prescriptionType,
            @RequestParam(required = false) String hospitalType,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String excludeOrderStatus,
            @RequestParam(required = false) String decoctionCenter,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String logisticsCompany,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String hospitalPrescriptionNo,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) String receiverPhone
    ) {
        AdminOrderSearchQuery query = new AdminOrderSearchQuery(
                parseQueryTime(startTime),
                parseQueryTime(endTime),
                institution,
                prescriptionType,
                hospitalType,
                orderStatus,
                excludeOrderStatus,
                decoctionCenter,
                deliveryType,
                logisticsCompany,
                province,
                keyword,
                hospitalPrescriptionNo,
                patientName,
                receiverPhone,
                1,
                5000
        );
        String csv = orderService.exportAdminOrdersCsv(query);
        String filename = "订单信息汇总-" + LocalDate.now(DEFAULT_QUERY_ZONE) + ".csv";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/admin/order-receipts")
    public ApiResponse<AdminOrderReceiptPage> listOrderReceipts(
            @RequestParam(required = false) String prescriptionNo,
            @RequestParam(required = false) String receiverName,
            @RequestParam(required = false) String receiverPhone,
            @RequestParam(required = false) String patientName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(orderService.listAdminOrderReceipts(new AdminOrderReceiptQuery(
                prescriptionNo,
                receiverName,
                receiverPhone,
                patientName,
                page,
                pageSize
        )));
    }

    @GetMapping("/admin/prescription-reprints")
    public ApiResponse<AdminPrescriptionReprintPage> listPrescriptionReprints(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String prescriptionNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(orderService.listAdminPrescriptionReprints(new AdminPrescriptionReprintQuery(
                parseQueryTime(startTime),
                parseQueryTime(endTime),
                prescriptionNo,
                page,
                pageSize
        )));
    }

    @GetMapping("/admin/prescription-reprints/{prescriptionNo}/print-payload")
    public ApiResponse<AdminPrescriptionPrintPayload> getPrescriptionPrintPayload(
            @PathVariable String prescriptionNo
    ) {
        return ApiResponse.ok(orderService.getAdminPrescriptionPrintPayload(prescriptionNo));
    }

    @GetMapping("/admin/manual-process-orders")
    public ApiResponse<AdminManualProcessPage> listManualProcessOrders(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String prescriptionType,
            @RequestParam(required = false) String hospitalType,
            @RequestParam(required = false) Integer isWithin,
            @RequestParam(required = false) String processType,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String prescriptionNo,
            @RequestParam(required = false) String hospitalPrescriptionNo,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) String doseRange,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(orderService.listAdminManualProcessOrders(new AdminManualProcessQuery(
                parseQueryTime(startTime),
                parseQueryTime(endTime),
                institution,
                prescriptionType,
                hospitalType,
                isWithin,
                processType,
                deliveryType,
                orderNo,
                prescriptionNo,
                hospitalPrescriptionNo,
                patientName,
                doseRange,
                page,
                pageSize
        )));
    }

    @PostMapping("/admin/manual-process-orders/{orderNo}/process")
    public ApiResponse<AdminManualProcessResult> manualProcessOrder(
            @PathVariable String orderNo,
            @RequestBody(required = false) AdminManualProcessCommand command
    ) {
        return ApiResponse.ok(orderService.manualProcessAdminOrder(orderNo, command));
    }

    @GetMapping("/admin/order-warehouses")
    public ApiResponse<AdminOrderWarehousePage> listOrderWarehouses(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String prescriptionType,
            @RequestParam(required = false) String hospitalType,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String decoctionCenter,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String logisticsCompany,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String prescriptionNo,
            @RequestParam(required = false) String hospitalPrescriptionNo,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) String receiverPhone,
            @RequestParam(required = false) String nodeTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(orderService.listAdminOrderWarehouses(new AdminOrderWarehouseQuery(
                parseQueryTime(startTime),
                parseQueryTime(endTime),
                institution,
                prescriptionType,
                hospitalType,
                orderStatus,
                decoctionCenter,
                deliveryType,
                logisticsCompany,
                province,
                orderNo,
                prescriptionNo,
                hospitalPrescriptionNo,
                patientName,
                receiverPhone,
                nodeTime,
                page,
                pageSize
        )));
    }

    @GetMapping(value = "/admin/order-warehouses/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportOrderWarehouses(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String prescriptionType,
            @RequestParam(required = false) String hospitalType,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String decoctionCenter,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String logisticsCompany,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String prescriptionNo,
            @RequestParam(required = false) String hospitalPrescriptionNo,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) String receiverPhone,
            @RequestParam(required = false) String nodeTime
    ) {
        AdminOrderWarehouseQuery query = new AdminOrderWarehouseQuery(
                parseQueryTime(startTime),
                parseQueryTime(endTime),
                institution,
                prescriptionType,
                hospitalType,
                orderStatus,
                decoctionCenter,
                deliveryType,
                logisticsCompany,
                province,
                orderNo,
                prescriptionNo,
                hospitalPrescriptionNo,
                patientName,
                receiverPhone,
                nodeTime,
                1,
                5000
        );
        String csv = orderService.exportAdminOrderWarehousesCsv(query);
        String filename = "订单仓库汇总-" + LocalDate.now(DEFAULT_QUERY_ZONE) + ".csv";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/admin/orders/{orderNo}")
    public ApiResponse<OrderCreateResult> getOrder(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getOrder(orderNo));
    }

    @GetMapping("/admin/orders/{orderNo}/detail")
    public ApiResponse<AdminOrderDetail> getOrderDetail(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getAdminOrderDetail(orderNo));
    }

    @GetMapping("/admin/operators")
    public ApiResponse<AdminOperatorPage> listOperators(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(orderService.listAdminOperators(new AdminOperatorQuery(
                keyword,
                enabled,
                page,
                pageSize
        )));
    }

    @PostMapping("/admin/operators")
    public ApiResponse<AdminOperatorRecord> createOperator(@RequestBody AdminOperatorCommand command) {
        return ApiResponse.ok(orderService.createAdminOperator(command));
    }

    @PatchMapping("/admin/operators/{operatorId}")
    public ApiResponse<AdminOperatorRecord> updateOperator(
            @PathVariable UUID operatorId,
            @RequestBody AdminOperatorCommand command
    ) {
        return ApiResponse.ok(orderService.updateAdminOperator(operatorId, command));
    }

    @GetMapping("/admin/institutions")
    public ApiResponse<AdminInstitutionPage> listInstitutions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String institutionType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(orderService.listAdminInstitutions(new AdminInstitutionQuery(
                keyword,
                status,
                institutionType,
                page,
                pageSize
        )));
    }

    @PostMapping("/admin/institutions")
    public ApiResponse<AdminInstitutionRecord> createInstitution(@RequestBody AdminInstitutionCommand command) {
        return ApiResponse.ok(orderService.createAdminInstitution(command));
    }

    @PatchMapping("/admin/institutions/{institutionId}")
    public ApiResponse<AdminInstitutionRecord> updateInstitution(
            @PathVariable UUID institutionId,
            @RequestBody AdminInstitutionCommand command
    ) {
        return ApiResponse.ok(orderService.updateAdminInstitution(institutionId, command));
    }

    @GetMapping("/admin/institution-apps")
    public ApiResponse<AdminInstitutionAppPage> listInstitutionApps(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(orderService.listAdminInstitutionApps(new AdminInstitutionAppQuery(
                keyword,
                institutionId,
                enabled,
                page,
                pageSize
        )));
    }

    @PostMapping("/admin/institution-apps")
    public ApiResponse<AdminInstitutionAppRecord> createInstitutionApp(
            @RequestBody AdminInstitutionAppCommand command
    ) {
        return ApiResponse.ok(orderService.createAdminInstitutionApp(command));
    }

    @PatchMapping("/admin/institution-apps/{appId}")
    public ApiResponse<AdminInstitutionAppRecord> updateInstitutionApp(
            @PathVariable UUID appId,
            @RequestBody AdminInstitutionAppCommand command
    ) {
        return ApiResponse.ok(orderService.updateAdminInstitutionApp(appId, command));
    }

    @GetMapping("/admin/institution-apis")
    public ApiResponse<AdminInstitutionApiPage> listInstitutionApis(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(orderService.listAdminInstitutionApis(new AdminInstitutionApiQuery(
                keyword,
                enabled,
                page,
                pageSize
        )));
    }

    @PostMapping("/admin/institution-apis")
    public ApiResponse<AdminInstitutionApiRecord> createInstitutionApi(
            @RequestBody AdminInstitutionApiCommand command
    ) {
        return ApiResponse.ok(orderService.createAdminInstitutionApi(command));
    }

    @PatchMapping("/admin/institution-apis/{apiId}")
    public ApiResponse<AdminInstitutionApiRecord> updateInstitutionApi(
            @PathVariable UUID apiId,
            @RequestBody AdminInstitutionApiCommand command
    ) {
        return ApiResponse.ok(orderService.updateAdminInstitutionApi(apiId, command));
    }

    @GetMapping("/admin/institution-api-permissions")
    public ApiResponse<AdminInstitutionApiPermissionPage> listInstitutionApiPermissions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) UUID apiId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(orderService.listAdminInstitutionApiPermissions(new AdminInstitutionApiPermissionQuery(
                keyword,
                institutionId,
                apiId,
                enabled,
                page,
                pageSize
        )));
    }

    @PostMapping("/admin/institution-api-permissions")
    public ApiResponse<AdminInstitutionApiPermissionRecord> createInstitutionApiPermission(
            @RequestBody AdminInstitutionApiPermissionCommand command
    ) {
        return ApiResponse.ok(orderService.createAdminInstitutionApiPermission(command));
    }

    @PatchMapping("/admin/institution-api-permissions/{permissionId}")
    public ApiResponse<AdminInstitutionApiPermissionRecord> updateInstitutionApiPermission(
            @PathVariable UUID permissionId,
            @RequestBody AdminInstitutionApiPermissionCommand command
    ) {
        return ApiResponse.ok(orderService.updateAdminInstitutionApiPermission(permissionId, command));
    }

    @GetMapping("/admin/institution-ip-whitelists")
    public ApiResponse<AdminInstitutionIpWhitelistPage> listInstitutionIpWhitelists(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) String ipRange,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(orderService.listAdminInstitutionIpWhitelists(new AdminInstitutionIpWhitelistQuery(
                keyword,
                institutionId,
                ipRange,
                enabled,
                page,
                pageSize
        )));
    }

    @PostMapping("/admin/institution-ip-whitelists")
    public ApiResponse<AdminInstitutionIpWhitelistRecord> createInstitutionIpWhitelist(
            @RequestBody AdminInstitutionIpWhitelistCommand command
    ) {
        return ApiResponse.ok(orderService.createAdminInstitutionIpWhitelist(command));
    }

    @PatchMapping("/admin/institution-ip-whitelists/{whitelistId}")
    public ApiResponse<AdminInstitutionIpWhitelistRecord> updateInstitutionIpWhitelist(
            @PathVariable UUID whitelistId,
            @RequestBody AdminInstitutionIpWhitelistCommand command
    ) {
        return ApiResponse.ok(orderService.updateAdminInstitutionIpWhitelist(whitelistId, command));
    }

    @GetMapping("/admin/logistics-special-rules")
    public ApiResponse<AdminLogisticsSpecialRulePage> listLogisticsSpecialRules(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(orderService.listAdminLogisticsSpecialRules(new AdminLogisticsSpecialRuleQuery(
                keyword,
                institutionId,
                enabled,
                page,
                pageSize
        )));
    }

    @PostMapping("/admin/logistics-special-rules")
    public ApiResponse<AdminLogisticsSpecialRuleRecord> createLogisticsSpecialRule(
            @RequestBody AdminLogisticsSpecialRuleCommand command
    ) {
        return ApiResponse.ok(orderService.createAdminLogisticsSpecialRule(command));
    }

    @PatchMapping("/admin/logistics-special-rules/{ruleId}")
    public ApiResponse<AdminLogisticsSpecialRuleRecord> updateLogisticsSpecialRule(
            @PathVariable UUID ruleId,
            @RequestBody AdminLogisticsSpecialRuleCommand command
    ) {
        return ApiResponse.ok(orderService.updateAdminLogisticsSpecialRule(ruleId, command));
    }

    @PatchMapping("/admin/orders/{orderNo}/address")
    public ApiResponse<AdminOrderAddressUpdateResult> updateOrderAddress(
            @PathVariable String orderNo,
            @RequestBody AdminOrderAddressUpdateCommand command
    ) {
        return ApiResponse.ok(orderService.updateAdminOrderAddress(orderNo, command));
    }

    @PatchMapping("/admin/orders/{orderNo}/prescriptions/{prescriptionId}")
    public ApiResponse<AdminOrderDetail.Prescription> updatePrescription(
            @PathVariable String orderNo,
            @PathVariable UUID prescriptionId,
            @RequestBody AdminPrescriptionUpdateCommand command
    ) {
        return ApiResponse.ok(orderService.updateAdminPrescription(orderNo, prescriptionId, command));
    }

    @PostMapping("/admin/orders/{orderNo}/cancel")
    public ApiResponse<AdminOrderCancelResult> cancelOrder(
            @PathVariable String orderNo,
            @RequestBody AdminOrderCancelCommand command
    ) {
        return ApiResponse.ok(orderService.cancelAdminOrder(orderNo, command));
    }

    @PostMapping("/admin/orders/{orderNo}/prescriptions/{prescriptionId}/initialize")
    public ApiResponse<AdminPrescriptionActionResult> initializePrescription(
            @PathVariable String orderNo,
            @PathVariable UUID prescriptionId,
            @RequestBody(required = false) AdminPrescriptionActionCommand command
    ) {
        return ApiResponse.ok(orderService.initializeAdminPrescription(orderNo, prescriptionId, command));
    }

    @PostMapping("/admin/orders/{orderNo}/prescriptions/{prescriptionId}/cancel")
    public ApiResponse<AdminPrescriptionActionResult> cancelPrescription(
            @PathVariable String orderNo,
            @PathVariable UUID prescriptionId,
            @RequestBody(required = false) AdminPrescriptionActionCommand command
    ) {
        return ApiResponse.ok(orderService.cancelAdminPrescription(orderNo, prescriptionId, command));
    }

    @PostMapping("/admin/orders/{orderNo}/initialize")
    public ApiResponse<AdminOrderInitializeResult> initializeOrder(
            @PathVariable String orderNo,
            @RequestBody AdminOrderInitializeCommand command
    ) {
        return ApiResponse.ok(orderService.initializeAdminOrder(orderNo, command));
    }

    @PostMapping("/admin/orders/{orderNo}/receipt")
    public ApiResponse<AdminOrderReceiptResult> receiptOrder(
            @PathVariable String orderNo,
            @RequestBody AdminOrderReceiptCommand command
    ) {
        return ApiResponse.ok(orderService.receiptAdminOrder(orderNo, command));
    }

    @PostMapping("/admin/order-receipts/batch")
    public ApiResponse<AdminBatchOrderReceiptResult> batchReceiptOrders(
            @RequestBody AdminBatchOrderReceiptCommand command
    ) {
        return ApiResponse.ok(orderService.batchReceiptAdminOrders(command));
    }

    @GetMapping("/admin/orders/{orderNo}/progress")
    public ApiResponse<OrderProgressSnapshot> getOrderProgress(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getOrderProgress(orderNo));
    }

    @GetMapping("/admin/review-tasks")
    @Deprecated
    public ApiResponse<List<WorkflowTaskSnapshot>> listReviewTasks() {
        return ApiResponse.ok(orderReviewTaskService.listPendingReviewTasks());
    }

    @PatchMapping("/admin/review-tasks/{taskId}/approve")
    @Deprecated
    public ApiResponse<OrderReviewResult> approveReviewTask(
            @PathVariable UUID taskId,
            @RequestBody OrderReviewCommand command
    ) {
        return ApiResponse.ok(orderReviewTaskService.approve(taskId, command));
    }

    @PatchMapping("/admin/review-tasks/{taskId}/reject")
    @Deprecated
    public ApiResponse<OrderReviewResult> rejectReviewTask(
            @PathVariable UUID taskId,
            @RequestBody OrderReviewCommand command
    ) {
        return ApiResponse.ok(orderReviewTaskService.reject(taskId, command));
    }

    private Instant parseQueryTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // 兼容老后台输入格式。
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
            // 兼容老后台输入格式。
        }
        try {
            return LocalDateTime.parse(trimmed, LEGACY_DATE_TIME_FORMATTER)
                    .atZone(DEFAULT_QUERY_ZONE)
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(trimmed)
                        .atZone(DEFAULT_QUERY_ZONE)
                        .toInstant();
            } catch (DateTimeParseException ex) {
                throw new BusinessException("INVALID_QUERY_TIME", "时间格式不正确，应为 yyyy-MM-dd HH:mm:ss");
            }
        }
    }
}
