package com.zhyf.gateway.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.security.AdminJwtCodec;
import com.zhyf.common.security.AdminPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminAuthenticationFilterTest {

    @Test
    void shouldRejectSignedTokenWhenSessionWasRevoked() throws Exception {
        AdminJwtCodec jwtCodec = mock(AdminJwtCodec.class);
        AdminPermissionPolicy permissionPolicy = mock(AdminPermissionPolicy.class);
        AdminSessionRepository sessionRepository = mock(AdminSessionRepository.class);
        AdminAuthenticationFilter filter = new AdminAuthenticationFilter(
                jwtCodec,
                new ObjectMapper(),
                permissionPolicy,
                sessionRepository
        );
        AdminPrincipal principal = new AdminPrincipal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tenant-a",
                "租户 A",
                "operator",
                "操作员",
                List.of("OPERATOR"),
                List.of(),
                List.of("order:read"),
                false,
                Instant.now(),
                Instant.now().plusSeconds(600),
                UUID.randomUUID(),
                UUID.randomUUID().toString()
        );
        when(jwtCodec.verify("signed-token")).thenReturn(principal);
        when(sessionRepository.isActive(principal)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/order-api/api/admin/orders");
        request.addHeader("Authorization", "Bearer signed-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("ADMIN_SESSION_REVOKED");
        verify(chain, never()).doFilter(request, response);
    }
}
