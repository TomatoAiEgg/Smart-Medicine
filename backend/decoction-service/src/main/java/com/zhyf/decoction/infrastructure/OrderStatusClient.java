package com.zhyf.decoction.infrastructure;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.decoction.config.DecoctionProperties;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrderStatusClient {

    private final RestClient restClient;
    private final DecoctionProperties properties;

    public OrderStatusClient(RestClient.Builder builder, DecoctionProperties properties) {
        this.restClient = builder.requestFactory(new JdkClientHttpRequestFactory()).build();
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
            throw new BusinessException("ORDER_SERVICE_EMPTY_RESPONSE", "Order service returned empty response");
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
