package com.zhyf.order.application;

import java.util.List;

public record AdminInstitutionIpWhitelistPage(
        List<AdminInstitutionIpWhitelistRecord> records,
        long total,
        int page,
        int pageSize
) {
}
