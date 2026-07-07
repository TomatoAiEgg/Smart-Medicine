package com.zhyf.order.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.order.application.OrderStatusUpdateCommand;
import com.zhyf.order.application.OrderStatusUpdateResult;
import com.zhyf.order.application.OrderStatusUpdateService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final OrderStatusUpdateService orderStatusUpdateService;

    public InternalOrderController(OrderStatusUpdateService orderStatusUpdateService) {
        this.orderStatusUpdateService = orderStatusUpdateService;
    }

    @PatchMapping("/{orderId}/status")
    public ApiResponse<OrderStatusUpdateResult> updateStatus(
            @PathVariable UUID orderId,
            @RequestBody OrderStatusUpdateCommand command
    ) {
        return ApiResponse.ok(orderStatusUpdateService.updateStatus(orderId, command));
    }
}
