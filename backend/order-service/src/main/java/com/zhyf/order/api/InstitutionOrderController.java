package com.zhyf.order.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.order.application.AdminOrderPage;
import com.zhyf.order.application.AdminOrderSearchQuery;
import com.zhyf.order.application.OrderCreateCommand;
import com.zhyf.order.application.OrderCreateResult;
import com.zhyf.order.application.OrderReviewCommand;
import com.zhyf.order.application.OrderReviewResult;
import com.zhyf.order.application.OrderReviewTaskService;
import com.zhyf.order.application.OrderService;
import com.zhyf.order.domain.OrderProgressSnapshot;
import com.zhyf.order.domain.WorkflowTaskSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
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

    @GetMapping("/admin/orders/{orderNo}")
    public ApiResponse<OrderCreateResult> getOrder(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getOrder(orderNo));
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
