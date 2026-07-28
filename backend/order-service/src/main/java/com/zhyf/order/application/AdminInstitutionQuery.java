package com.zhyf.order.application;

public record AdminInstitutionQuery(
        String keyword,
        String status,
        String institutionType,
        int page,
        int pageSize
) {
}
