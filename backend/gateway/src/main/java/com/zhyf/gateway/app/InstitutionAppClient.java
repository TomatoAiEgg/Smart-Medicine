package com.zhyf.gateway.app;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.gateway.config.GatewayProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InstitutionAppClient {

    private final RestClient restClient;
    private final GatewayProperties properties;

    public InstitutionAppClient(RestClient.Builder builder, GatewayProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public InstitutionAppView getEnabledApp(String appKey) {
        String encodedAppKey = URLEncoder.encode(appKey, StandardCharsets.UTF_8);
        ApiResponse<InstitutionAppView> response = restClient.get()
                .uri(properties.authInstitutionBaseUrl() + "/internal/institution-apps/" + encodedAppKey)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null) {
            throw new BusinessException("AUTH_EMPTY_RESPONSE", "机构配置服务无响应");
        }
        if (!"0".equals(response.code())) {
            throw new BusinessException(response.code(), response.message());
        }
        return response.data();
    }
}
