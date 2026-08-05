package com.zhyf.gateway.admin;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class AdminPermissionPolicy {

    private static final List<RouteGroup> ORDER_ROUTE_GROUPS = List.of(
            new RouteGroup("order:export", path -> path.startsWith("/order-api/api/admin/export-tasks")),
            new RouteGroup("system", path -> startsWithAny(path,
                    "/order-api/api/admin/operators",
                    "/order-api/api/admin/operator-roles",
                    "/order-api/api/admin/dict-",
                    "/order-api/api/admin/system-configs",
                    "/order-api/api/admin/decoct-centers")),
            new RouteGroup("drug", path -> startsWithAny(path,
                    "/order-api/api/admin/herbs",
                    "/order-api/api/admin/herb-")),
            new RouteGroup("institution", path -> startsWithAny(path,
                    "/order-api/api/admin/institutions",
                    "/order-api/api/admin/institution-")),
            new RouteGroup("logistics", path -> startsWithAny(path,
                    "/order-api/api/admin/logistics-",
                    "/order-api/api/admin/order-merges")),
            new RouteGroup("order", path -> path.startsWith("/order-api/api/admin/"))
    );

    public Optional<String> requiredPermission(String method, String requestPath) {
        String normalizedMethod = method == null ? "" : method.toUpperCase(Locale.ROOT);
        if (requestPath == null || requestPath.isBlank()) {
            return Optional.empty();
        }
        if (requestPath.startsWith("/auth-api/api/admin/auth/")) {
            return Optional.of("authenticated");
        }
        if (requestPath.startsWith("/order-api/api/admin/") && requestPath.endsWith(".csv")) {
            return Optional.of("order:export");
        }
        for (RouteGroup group : ORDER_ROUTE_GROUPS) {
            if (group.matcher().test(requestPath)) {
                if ("order:export".equals(group.permissionPrefix())) {
                    return Optional.of("order:export");
                }
                return Optional.of(group.permissionPrefix() + action(normalizedMethod));
            }
        }
        if (requestPath.startsWith("/workflow-api/api/admin/workflow/")) {
            return Optional.of("workflow" + action(normalizedMethod));
        }
        if (requestPath.startsWith("/message-api/api/admin/sms/send-single")) {
            return Optional.of("sms:send");
        }
        if (requestPath.startsWith("/message-api/api/admin/sms/")) {
            return Optional.of("sms" + action(normalizedMethod));
        }
        if (startsWithAny(requestPath,
                "/decoction-api/admin/decoction/",
                "/decoction-api/simulator/")) {
            return Optional.of("decoction" + action(normalizedMethod));
        }
        if (requestPath.startsWith("/ops-api/api/admin/ops/")) {
            return Optional.of("ops" + action(normalizedMethod));
        }
        if (requestPath.startsWith("/logistics-api/api/admin/logistics/")) {
            return Optional.of("logistics" + action(normalizedMethod));
        }
        if (requestPath.startsWith("/callback-api/api/admin/callback-records")) {
            return Optional.of(isRead(normalizedMethod) ? "callback:read" : "callback:replay");
        }
        if (requestPath.startsWith("/portal-api/api/portal/")) {
            return Optional.of("portal" + action(normalizedMethod));
        }
        if (requestPath.startsWith("/report-api/api/admin/reports/")) {
            return Optional.of(requestPath.endsWith(".csv") ? "report:export" : "report:read");
        }
        if (requestPath.startsWith("/integration-api/api/admin/integration/")) {
            return Optional.of(isRead(normalizedMethod) ? "integration:read" : "integration:retry");
        }
        if (requestPath.startsWith("/integration-api/api/integration/")) {
            return Optional.of(isRead(normalizedMethod) ? "integration:read" : "integration:write");
        }
        return Optional.empty();
    }

    private String action(String method) {
        return isRead(method) ? ":read" : ":write";
    }

    private boolean isRead(String method) {
        return "GET".equals(method) || "HEAD".equals(method);
    }

    private static boolean startsWithAny(String path, String... prefixes) {
        for (String prefix : prefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private record RouteGroup(String permissionPrefix, Predicate<String> matcher) {
    }
}
