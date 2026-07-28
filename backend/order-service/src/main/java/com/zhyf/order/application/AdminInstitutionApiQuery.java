package com.zhyf.order.application;

public record AdminInstitutionApiQuery(
        String keyword,
        Boolean enabled,
        int page,
        int pageSize
) {
}
