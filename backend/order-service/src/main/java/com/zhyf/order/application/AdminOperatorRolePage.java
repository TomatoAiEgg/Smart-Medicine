package com.zhyf.order.application;

import java.util.List;

public record AdminOperatorRolePage(
        List<AdminOperatorRoleRecord> records,
        long total,
        int page,
        int pageSize
) {
}
