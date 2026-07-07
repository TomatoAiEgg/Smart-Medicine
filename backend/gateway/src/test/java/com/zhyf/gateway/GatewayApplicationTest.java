package com.zhyf.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:15432/zhyf_saas",
        "spring.datasource.username=postgres",
        "spring.datasource.password=test",
        "zhyf.gateway.auth-institution-base-url=http://localhost:18081",
        "zhyf.gateway.order-service-base-url=http://localhost:18082",
        "zhyf.gateway.signature-timeout-seconds=300"
})
class GatewayApplicationTest {

    @Test
    void contextLoads() {
    }
}
