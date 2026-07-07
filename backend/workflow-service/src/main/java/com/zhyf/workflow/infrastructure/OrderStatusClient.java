package com.zhyf.workflow.infrastructure;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.workflow.config.WorkflowProperties;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrderStatusClient {

    private final RestClient restClient;
    private final WorkflowProperties properties;

    public OrderStatusClient(RestClient.Builder builder, WorkflowProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public OrderStatusUpdateResult updateStatus(
            UUID orderId,
            String targetStatus,
            String operatorType,
            String source
    ) {
        OrderStatusUpdateCommand command = new OrderStatusUpdateCommand(targetStatus, operatorType, source);
        ApiResponse<OrderStatusUpdateResult> response = restClient.patch()
                .uri(properties.getOrderServiceBaseUrl() + "/internal/orders/" + orderId + "/status")
                .body(command)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null) {
            throw new BusinessException("ORDER_SERVICE_EMPTY_RESPONSE", "订单服务无响应");
        }
        if (!"0".equals(response.code())) {
            throw new BusinessException(response.code(), response.message());
        }
        return response.data();
    }

    public record OrderStatusUpdateCommand(
            String targetStatus,
            String operatorType,
            String source
    ) {
    }

    public record OrderStatusUpdateResult(
            UUID orderId,
            String orderNo,
            String fromStatus,
            String toStatus
    ) {
    }
}
