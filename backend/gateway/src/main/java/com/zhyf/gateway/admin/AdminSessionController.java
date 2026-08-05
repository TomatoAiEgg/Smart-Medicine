package com.zhyf.gateway.admin;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.security.AdminPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth-api/api/admin/auth")
public class AdminSessionController {

    @GetMapping("/me")
    public ApiResponse<AdminSession> current(HttpServletRequest request) {
        AdminPrincipal principal = principal(request);
        return ApiResponse.ok(new AdminSession(
                principal.userId(),
                principal.tenantId(),
                principal.tenantCode(),
                principal.tenantName(),
                principal.username(),
                principal.displayName(),
                principal.roleCodes(),
                principal.institutionIds(),
                principal.permissions(),
                principal.tenantWide(),
                principal.expiresAt()
        ));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        principal(request);
        return ApiResponse.ok(null);
    }

    private AdminPrincipal principal(HttpServletRequest request) {
        return (AdminPrincipal) request.getAttribute(AdminAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
    }

    public record AdminSession(
            UUID userId,
            UUID tenantId,
            String tenantCode,
            String tenantName,
            String username,
            String displayName,
            List<String> roleCodes,
            List<UUID> institutionIds,
            List<String> permissions,
            boolean tenantWide,
            Instant expiresAt
    ) {
    }
}
