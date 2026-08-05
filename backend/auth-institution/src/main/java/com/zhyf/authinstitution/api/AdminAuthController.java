package com.zhyf.authinstitution.api;

import com.zhyf.authinstitution.app.AdminAuthRecords;
import com.zhyf.authinstitution.app.AdminAuthService;
import com.zhyf.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService authService;

    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminAuthRecords.LoginResult> login(
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(authService.login(new AdminAuthRecords.LoginCommand(
                request.tenantCode(),
                request.username(),
                request.password(),
                servletRequest.getHeader("X-Request-Id"),
                clientIp(servletRequest)
        )));
    }

    @PostMapping("/refresh")
    public ApiResponse<AdminAuthRecords.LoginResult> refresh(
            @RequestBody RefreshRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(authService.refresh(new AdminAuthRecords.RefreshCommand(
                request.refreshToken(),
                servletRequest.getHeader("X-Request-Id"),
                clientIp(servletRequest)
        )));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp.trim();
    }

    public record LoginRequest(String tenantCode, String username, String password) {
    }

    public record RefreshRequest(String refreshToken) {
    }
}
