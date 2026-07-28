package com.zhyf.order.application;

public record AdminInstitutionCommand(
        String institutionCode,
        String institutionName,
        String institutionType,
        String status,
        String storageType
) {
}
