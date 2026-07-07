package com.zhyf.logistics.infrastructure;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.logistics.config.LogisticsProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CallbackClient {

    private static final Logger log = LoggerFactory.getLogger(CallbackClient.class);

    private final RestClient restClient;
    private final LogisticsProperties properties;

    public CallbackClient(RestClient.Builder builder, LogisticsProperties properties) {
        this.restClient = builder.requestFactory(new JdkClientHttpRequestFactory()).build();
        this.properties = properties;
    }

    public void createCallback(UUID orderId, String callbackType, String businessId, String businessStatus, String source) {
        try {
            ApiResponse<Object> response = restClient.post()
                    .uri(properties.getCallbackServiceBaseUrl() + "/internal/callback-records")
                    .body(new CallbackCreateCommand(orderId, callbackType, businessId, businessStatus, source))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || !"0".equals(response.code())) {
                log.warn("callback record create failed orderId={} callbackType={} businessId={} response={}",
                        orderId, callbackType, businessId, response);
            }
        } catch (RuntimeException ex) {
            log.warn("callback-service unavailable orderId={} callbackType={} businessId={} error={}",
                    orderId, callbackType, businessId, ex.getMessage());
        }
    }

    public record CallbackCreateCommand(
            UUID orderId,
            String callbackType,
            String businessId,
            String businessStatus,
            String source
    ) {
    }
}
