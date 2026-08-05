package com.zhyf.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration
@ConditionalOnClass({JdbcTemplate.class, PlatformTransactionManager.class})
@ConditionalOnProperty(
        prefix = "zhyf.admin-data-scope",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(AdminDataScopeProperties.class)
public class AdminDataScopeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AdminDataScopeFilter adminDataScopeFilter(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper
    ) {
        return new AdminDataScopeFilter(jdbcTemplate, transactionManager, objectMapper);
    }
}
