package com.zhyf.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhyf.gateway.admin.AdminPermissionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:15432/zhyf_saas",
        "spring.datasource.username=postgres",
        "spring.datasource.password=test",
        "zhyf.gateway.auth-institution-base-url=http://localhost:18081",
        "zhyf.gateway.order-service-base-url=http://localhost:18082",
        "zhyf.gateway.signature-timeout-seconds=300",
        "zhyf.admin-gateway.jwt-secret=test-admin-jwt-secret-with-at-least-32-bytes"
})
class GatewayApplicationTest {

    @Test
    void contextLoads() {
    }

    @Test
    void shouldResolveFineGrainedAdminPermissionsAndRejectInternalRoutes() {
        AdminPermissionPolicy policy = new AdminPermissionPolicy();

        assertThat(policy.requiredPermission("GET", "/order-api/api/admin/orders"))
                .contains("order:read");
        assertThat(policy.requiredPermission("PATCH", "/order-api/api/admin/operators/1"))
                .contains("system:write");
        assertThat(policy.requiredPermission("GET", "/report-api/api/admin/reports/overview.csv"))
                .contains("report:export");
        assertThat(policy.requiredPermission("POST", "/decoction-api/simulator/mes/tasks/T1/start"))
                .contains("decoction:write");
        assertThat(policy.requiredPermission("POST", "/integration-api/api/integration/community/messages"))
                .contains("integration:write");
        assertThat(policy.requiredPermission("GET", "/order-api/internal/orders/1"))
                .isEmpty();
    }
}
