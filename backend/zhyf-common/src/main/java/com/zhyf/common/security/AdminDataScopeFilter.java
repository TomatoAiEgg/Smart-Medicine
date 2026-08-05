package com.zhyf.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.api.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminDataScopeFilter extends OncePerRequestFilter {

    private static final List<String> REQUIRED_PREFIXES = List.of("/api/admin/", "/admin/");
    private static final String SCOPED_DATABASE_ROLE = "zhyf_admin_scoped";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public AdminDataScopeFilter(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean hasAdminContext = StringUtils.hasText(request.getHeader(AdminSecurityHeaders.TENANT_ID));
        boolean requiresAdminContext = REQUIRED_PREFIXES.stream().anyMatch(path::startsWith);
        return !hasAdminContext && !requiresAdminContext;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        AdminRequestContext context;
        try {
            context = parseContext(request);
        } catch (IllegalArgumentException ex) {
            writeUnauthorized(response, ex.getMessage());
            return;
        }

        AdminRequestContextHolder.set(context);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                applyDatabaseScope(context);
                try {
                    filterChain.doFilter(request, response);
                } catch (IOException | ServletException ex) {
                    throw new FilterChainException(ex);
                }
            });
        } catch (FilterChainException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            if (ex.getCause() instanceof ServletException servletException) {
                throw servletException;
            }
            throw ex;
        } finally {
            AdminRequestContextHolder.clear();
        }
    }

    private AdminRequestContext parseContext(HttpServletRequest request) {
        UUID userId = requiredUuid(request, AdminSecurityHeaders.USER_ID);
        UUID tenantId = requiredUuid(request, AdminSecurityHeaders.TENANT_ID);
        String username = requiredText(request, AdminSecurityHeaders.USERNAME);
        String tenantWideHeader = requiredText(request, AdminSecurityHeaders.TENANT_WIDE);
        if (!"true".equalsIgnoreCase(tenantWideHeader) && !"false".equalsIgnoreCase(tenantWideHeader)) {
            throw new IllegalArgumentException("管理端数据范围标识无效");
        }
        return new AdminRequestContext(
                userId,
                username,
                tenantId,
                csv(request.getHeader(AdminSecurityHeaders.ROLE_CODES)),
                uuidCsv(request.getHeader(AdminSecurityHeaders.INSTITUTION_IDS)),
                csv(request.getHeader(AdminSecurityHeaders.PERMISSIONS)),
                Boolean.parseBoolean(tenantWideHeader)
        );
    }

    private void applyDatabaseScope(AdminRequestContext context) {
        jdbcTemplate.queryForObject(
                "select set_config('zhyf.admin_tenant_id', ?, true)",
                String.class,
                context.tenantId().toString()
        );
        jdbcTemplate.queryForObject(
                "select set_config('zhyf.admin_tenant_wide', ?, true)",
                String.class,
                Boolean.toString(context.tenantWide())
        );
        jdbcTemplate.queryForObject(
                "select set_config('zhyf.admin_institution_ids', ?, true)",
                String.class,
                String.join(",", context.institutionIds().stream().map(UUID::toString).toList())
        );
        jdbcTemplate.execute("set local role " + SCOPED_DATABASE_ROLE);
    }

    private UUID requiredUuid(HttpServletRequest request, String header) {
        try {
            return UUID.fromString(requiredText(request, header));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("管理端身份上下文无效", ex);
        }
    }

    private String requiredText(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("缺少管理端身份上下文");
        }
        return value.trim();
    }

    private List<String> csv(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<UUID> uuidCsv(String value) {
        try {
            return csv(value).stream().map(UUID::fromString).toList();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("管理端机构范围无效", ex);
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail("ADMIN_CONTEXT_REQUIRED", message));
    }

    private static final class FilterChainException extends RuntimeException {

        private FilterChainException(Exception cause) {
            super(cause);
        }
    }
}
