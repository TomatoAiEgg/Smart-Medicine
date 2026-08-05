package com.zhyf.order.application;

public record AdminRbacPermissionOption(
        String permissionCode,
        String permissionName,
        String resourceType,
        String httpMethod,
        String resourcePattern
) {
}
