package com.zhyf.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.security.AdminJwtCodec;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminGatewayConfiguration {

    @Bean
    public AdminJwtCodec adminJwtCodec(AdminGatewayProperties properties, ObjectMapper objectMapper) {
        return new AdminJwtCodec(properties.getJwtSecret(), properties.getIssuer(), objectMapper);
    }

    @Bean
    public HttpClient adminProxyHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }
}
