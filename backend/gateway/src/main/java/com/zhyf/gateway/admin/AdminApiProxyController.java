package com.zhyf.gateway.admin;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.security.AdminPrincipal;
import com.zhyf.gateway.config.AdminGatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminApiProxyController {

    private static final List<String> REQUEST_HEADERS = List.of(
            HttpHeaders.ACCEPT,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.IF_NONE_MATCH,
            HttpHeaders.IF_MODIFIED_SINCE,
            "Idempotency-Key"
    );
    private static final List<String> RESPONSE_HEADERS = List.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.CONTENT_DISPOSITION,
            HttpHeaders.CACHE_CONTROL,
            HttpHeaders.ETAG,
            HttpHeaders.LAST_MODIFIED
    );

    private final HttpClient httpClient;
    private final Map<String, String> targets;

    public AdminApiProxyController(HttpClient adminProxyHttpClient, AdminGatewayProperties properties) {
        this.httpClient = adminProxyHttpClient;
        this.targets = targets(properties);
    }

    @RequestMapping({
            "/auth-api/**",
            "/order-api/**",
            "/workflow-api/**",
            "/message-api/**",
            "/decoction-api/**",
            "/ops-api/**",
            "/logistics-api/**",
            "/callback-api/**",
            "/portal-api/**",
            "/report-api/**",
            "/integration-api/**"
    })
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        ProxyTarget target = resolveTarget(request);
        try {
            byte[] body = request.getInputStream().readAllBytes();
            HttpRequest.BodyPublisher bodyPublisher = body.length == 0
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(target.uri())
                    .timeout(Duration.ofSeconds(60))
                    .method(request.getMethod(), bodyPublisher);
            for (String header : REQUEST_HEADERS) {
                String value = request.getHeader(header);
                if (value != null && !value.isBlank()) {
                    builder.header(header, value);
                }
            }
            builder.header("X-Request-Id", requestId(request));
            builder.header("X-Real-IP", clientIp(request));
            AdminPrincipal principal = principal(request);
            if (principal != null) {
                builder.header("X-Admin-User-Id", principal.userId().toString());
                builder.header("X-Admin-Username", principal.username());
                builder.header("X-Admin-Tenant-Id", principal.tenantId().toString());
                builder.header("X-Admin-Role-Codes", String.join(",", principal.roleCodes()));
                builder.header("X-Admin-Institution-Ids", joinUuids(principal.institutionIds()));
            }
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            HttpHeaders responseHeaders = new HttpHeaders();
            for (String header : RESPONSE_HEADERS) {
                response.headers().firstValue(header).ifPresent(value -> responseHeaders.set(header, value));
            }
            return ResponseEntity.status(response.statusCode()).headers(responseHeaders).body(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("ADMIN_BACKEND_INTERRUPTED", "后台服务请求已中断");
        } catch (IOException | IllegalArgumentException ex) {
            throw new BusinessException("ADMIN_BACKEND_UNAVAILABLE", "后台服务暂时不可用");
        }
    }

    private ProxyTarget resolveTarget(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (Map.Entry<String, String> entry : targets.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                String downstreamPath = path.substring(entry.getKey().length() - 1);
                String query = request.getQueryString();
                String target = trimTrailingSlash(entry.getValue()) + downstreamPath
                        + (query == null || query.isBlank() ? "" : "?" + query);
                return new ProxyTarget(URI.create(target));
            }
        }
        throw new BusinessException("ADMIN_BACKEND_NOT_FOUND", "后台服务路由不存在");
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

    private String joinUuids(List<UUID> values) {
        return String.join(",", values.stream().map(UUID::toString).toList());
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private Map<String, String> targets(AdminGatewayProperties properties) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("/auth-api/", properties.getAuthInstitutionBaseUrl());
        values.put("/order-api/", properties.getOrderServiceBaseUrl());
        values.put("/workflow-api/", properties.getWorkflowServiceBaseUrl());
        values.put("/message-api/", properties.getMessageServiceBaseUrl());
        values.put("/decoction-api/", properties.getDecoctionServiceBaseUrl());
        values.put("/ops-api/", properties.getOpsServiceBaseUrl());
        values.put("/logistics-api/", properties.getLogisticsServiceBaseUrl());
        values.put("/callback-api/", properties.getCallbackServiceBaseUrl());
        values.put("/portal-api/", properties.getPortalServiceBaseUrl());
        values.put("/report-api/", properties.getReportServiceBaseUrl());
        values.put("/integration-api/", properties.getIntegrationServiceBaseUrl());
        return Map.copyOf(values);
    }

    private record ProxyTarget(URI uri) {
    }
}
