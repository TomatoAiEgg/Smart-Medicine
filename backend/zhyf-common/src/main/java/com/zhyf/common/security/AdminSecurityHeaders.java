package com.zhyf.common.security;

public final class AdminSecurityHeaders {

    public static final String USER_ID = "X-Admin-User-Id";
    public static final String USERNAME = "X-Admin-Username";
    public static final String TENANT_ID = "X-Admin-Tenant-Id";
    public static final String ROLE_CODES = "X-Admin-Role-Codes";
    public static final String INSTITUTION_IDS = "X-Admin-Institution-Ids";
    public static final String PERMISSIONS = "X-Admin-Permissions";
    public static final String TENANT_WIDE = "X-Admin-Tenant-Wide";

    private AdminSecurityHeaders() {
    }
}
