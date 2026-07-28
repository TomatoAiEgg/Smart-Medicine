package com.zhyf.order.application;

import java.util.List;

public record AdminInstitutionApiPage(
        List<AdminInstitutionApiRecord> records,
        long total,
        int page,
        int pageSize
) {
}
