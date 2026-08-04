package com.zhyf.authinstitution.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.security.AdminJwtCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminAuthConfiguration {

    @Bean
    public AdminJwtCodec adminJwtCodec(AdminAuthProperties properties, ObjectMapper objectMapper) {
        return new AdminJwtCodec(properties.getJwtSecret(), properties.getIssuer(), objectMapper);
    }

    @Bean
    public PasswordEncoder adminPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
