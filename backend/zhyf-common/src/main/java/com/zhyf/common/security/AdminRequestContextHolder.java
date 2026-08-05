package com.zhyf.common.security;

import java.util.Optional;

public final class AdminRequestContextHolder {

    private static final ThreadLocal<AdminRequestContext> CURRENT = new ThreadLocal<>();

    private AdminRequestContextHolder() {
    }

    public static Optional<AdminRequestContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    static void set(AdminRequestContext context) {
        CURRENT.set(context);
    }

    static void clear() {
        CURRENT.remove();
    }
}
