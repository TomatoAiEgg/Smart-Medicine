package com.zhyf.order.application;

import java.util.List;

public record AdminRbacRolePage(
        List<AdminRbacRoleRecord> records,
        long total,
        int page,
        int pageSize
) {
}
