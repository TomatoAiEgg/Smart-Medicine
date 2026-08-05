package com.zhyf.gateway.admin;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.security.AdminPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth-api/api/admin/auth")
public class AdminSessionController {

    private final AdminSessionRepository sessionRepository;

    public AdminSessionController(AdminSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

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
        sessionRepository.revokeCurrentSession(
                principal(request),
                requestId(request),
                clientIp(request)
        );
        return ApiResponse.ok(null);
    }

    @PostMapping("/users/{userId}/revoke-sessions")
    public ApiResponse<SessionRevocationResult> revokeUserSessions(
            @PathVariable UUID userId,
            HttpServletRequest request
    ) {
        AdminPrincipal principal = principal(request);
        if (!principal.tenantWide()) {
            throw new BusinessException("ADMIN_TENANT_WIDE_REQUIRED", "只有租户全域管理员可以强制下线账号");
        }
        if (principal.userId().equals(userId)) {
            throw new BusinessException("ADMIN_SELF_REVOKE_FORBIDDEN", "当前账号请使用退出登录");
        }
        int revoked = sessionRepository.revokeUserSessions(
                principal,
                userId,
                requestId(request),
                clientIp(request)
        );
        return ApiResponse.ok(new SessionRevocationResult(userId, revoked));
    }

    private AdminPrincipal principal(HttpServletRequest request) {
        return (AdminPrincipal) request.getAttribute(AdminAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp.trim();
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

    public record SessionRevocationResult(UUID userId, int revokedSessions) {
    }
}
