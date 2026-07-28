package com.zhyf.order.application;

import java.util.List;

public record AdminInstitutionPage(
        List<AdminInstitutionRecord> records,
        long total,
        int page,
        int pageSize
) {
}
