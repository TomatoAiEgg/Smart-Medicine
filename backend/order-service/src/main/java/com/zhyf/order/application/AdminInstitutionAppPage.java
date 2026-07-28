package com.zhyf.order.application;

import java.util.List;

public record AdminInstitutionAppPage(
        List<AdminInstitutionAppRecord> records,
        long total,
        int page,
        int pageSize
) {
}
