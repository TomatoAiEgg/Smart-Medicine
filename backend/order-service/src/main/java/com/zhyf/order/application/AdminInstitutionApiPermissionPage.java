package com.zhyf.order.application;

import java.util.List;

public record AdminInstitutionApiPermissionPage(
        List<AdminInstitutionApiPermissionRecord> records,
        long total,
        int page,
        int pageSize
) {
}
