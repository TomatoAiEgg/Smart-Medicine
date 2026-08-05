package com.zhyf.gateway.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.security.AdminJwtCodec;
import com.zhyf.common.security.AdminPrincipal;
import com.zhyf.common.security.AdminTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ATTRIBUTE = AdminAuthenticationFilter.class.getName() + ".principal";
    private static final String LOGIN_PATH = "/auth-api/api/admin/auth/login";
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/auth-api/",
            "/order-api/",
            "/workflow-api/",
            "/message-api/",
            "/decoction-api/",
            "/ops-api/",
            "/logistics-api/",
            "/callback-api/",
            "/portal-api/",
            "/report-api/",
            "/integration-api/"
    );

    private final AdminJwtCodec jwtCodec;
    private final ObjectMapper objectMapper;
    private final AdminPermissionPolicy permissionPolicy;

    public AdminAuthenticationFilter(
            AdminJwtCodec jwtCodec,
            ObjectMapper objectMapper,
            AdminPermissionPolicy permissionPolicy
    ) {
        this.jwtCodec = jwtCodec;
        this.objectMapper = objectMapper;
        this.permissionPolicy = permissionPolicy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || LOGIN_PATH.equals(path)
                || PROTECTED_PREFIXES.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            writeUnauthorized(response, "ADMIN_TOKEN_REQUIRED", "请先登录管理后台");
            return;
        }
        try {
            AdminPrincipal principal = jwtCodec.verify(authorization.substring(7).trim());
            String requiredPermission = permissionPolicy
                    .requiredPermission(request.getMethod(), request.getRequestURI())
                    .orElse(null);
            if (requiredPermission == null) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        "ADMIN_ROUTE_FORBIDDEN", "当前管理端路由未开放访问");
                return;
            }
            if (!"authenticated".equals(requiredPermission)
                    && !principal.permissions().contains(requiredPermission)) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        "ADMIN_PERMISSION_REQUIRED", "当前账号缺少操作权限: " + requiredPermission);
                return;
            }
            request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
            filterChain.doFilter(request, response);
        } catch (AdminTokenException ex) {
            writeUnauthorized(response, "ADMIN_TOKEN_INVALID", ex.getMessage());
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String code, String message) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, code, message);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(code, message));
    }
}
