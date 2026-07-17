package com.zhyf.order.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhyf.common.api.ApiResponse;
import com.zhyf.order.application.OrderCreateCommand;
import com.zhyf.order.application.OrderCreateResult;
import com.zhyf.order.application.OrderReviewCommand;
import com.zhyf.order.application.OrderReviewResult;
import com.zhyf.order.application.OrderReviewTaskService;
import com.zhyf.order.application.OrderService;
import com.zhyf.order.domain.OrderProgressSnapshot;
import com.zhyf.order.domain.WorkflowTaskSnapshot;
import jakarta.servlet.http.HttpServletRequest;
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
}
