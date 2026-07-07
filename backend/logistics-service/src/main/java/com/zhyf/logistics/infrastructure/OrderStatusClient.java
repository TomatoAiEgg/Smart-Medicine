package com.zhyf.logistics.infrastructure;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.logistics.config.LogisticsProperties;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrderStatusClient {

    private final RestClient restClient;
    private final LogisticsProperties properties;

    public OrderStatusClient(RestClient.Builder builder, LogisticsProperties properties) {
        this.restClient = builder.requestFactory(new JdkClientHttpRequestFactory()).build();
        this.properties = properties;
    }

    public void updateStatus(UUID orderId, String targetStatus, String source) {
        ApiResponse<OrderStatusUpdateResult> response = restClient.patch()
                .uri(properties.getOrderServiceBaseUrl() + "/internal/orders/" + orderId + "/status")
                .body(new OrderStatusUpdateCommand(targetStatus, "LOGISTICS", source))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null) {
            throw new BusinessException("ORDER_SERVICE_EMPTY_RESPONSE", "Order service returned empty response");
        }
        if (!"0".equals(response.code())) {
            throw new BusinessException(response.code(), response.message());
        }
    }

    public record OrderStatusUpdateCommand(String targetStatus, String operatorType, String source) {
    }

    public record OrderStatusUpdateResult(UUID orderId, String orderNo, String fromStatus, String toStatus) {
    }
}
