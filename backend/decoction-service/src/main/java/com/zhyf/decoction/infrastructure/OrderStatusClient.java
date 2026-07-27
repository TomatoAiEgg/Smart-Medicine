package com.zhyf.decoction.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.decoction.config.DecoctionProperties;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrderStatusClient {

    private final RestClient restClient;
    private final DecoctionProperties properties;
    private final ObjectMapper objectMapper;

    public OrderStatusClient(RestClient.Builder builder, DecoctionProperties properties, ObjectMapper objectMapper) {
        this.restClient = builder.requestFactory(new JdkClientHttpRequestFactory()).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
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
                .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                    throw parseOrderServiceError(clientResponse);
                })
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null) {
            throw new BusinessException("ORDER_SERVICE_EMPTY_RESPONSE", "Order service returned empty response");
        }
        if (!response.success()) {
            throw new BusinessException(response.code(), response.message());
        }
        return response.data();
    }

    private BusinessException parseOrderServiceError(ClientHttpResponse response) {
        try {
            ApiResponse<Object> error = objectMapper.readValue(response.getBody(), new TypeReference<>() {
            });
            if (error != null && !error.success()) {
                return new BusinessException(error.code(), error.message());
            }
        } catch (IOException ignored) {
            return new BusinessException("ORDER_SERVICE_ERROR", "订单服务状态更新失败");
        }
        return new BusinessException("ORDER_SERVICE_ERROR", "订单服务状态更新失败");
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
