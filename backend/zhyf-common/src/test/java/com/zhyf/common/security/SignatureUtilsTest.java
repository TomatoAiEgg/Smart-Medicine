package com.zhyf.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SignatureUtilsTest {

    @Test
    void hmacSha256HexShouldBeStable() {
        String signature = SignatureUtils.hmacSha256Hex("demo-secret", "demo-app\n1000\nabc");

        assertThat(signature).hasSize(64);
        assertThat(SignatureUtils.constantTimeEquals(signature, signature)).isTrue();
        assertThat(SignatureUtils.constantTimeEquals(signature, "bad")).isFalse();
    }

    @Test
    void adminTokenShouldKeepPermissionsAndTenantScope() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AdminJwtCodec codec = new AdminJwtCodec(
                "test-admin-jwt-secret-with-at-least-32-bytes",
                "test-admin",
                new ObjectMapper()
        );

        AdminJwtCodec.IssuedAdminToken issued = codec.issue(
                userId,
                tenantId,
                "tenant-a",
                "租户 A",
                "admin",
                "管理员",
                List.of("TENANT_ADMIN"),
                List.of(),
                List.of("order:read", "order:write"),
                true,
                Duration.ofMinutes(10)
        );

        AdminPrincipal principal = codec.verify(issued.token());
        assertThat(principal.tenantId()).isEqualTo(tenantId);
        assertThat(principal.permissions()).containsExactly("order:read", "order:write");
        assertThat(principal.tenantWide()).isTrue();
    }
}
