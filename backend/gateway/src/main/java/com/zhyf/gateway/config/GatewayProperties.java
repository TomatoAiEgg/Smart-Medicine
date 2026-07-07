package com.zhyf.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zhyf.gateway")
public record GatewayProperties(
        String authInstitutionBaseUrl,
        String orderServiceBaseUrl,
        long signatureTimeoutSeconds
) {
}
