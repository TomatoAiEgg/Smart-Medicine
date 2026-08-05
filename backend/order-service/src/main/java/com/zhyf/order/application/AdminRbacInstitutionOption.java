package com.zhyf.order.application;

import java.util.UUID;

public record AdminRbacInstitutionOption(
        UUID institutionId,
        String institutionCode,
        String institutionName,
        String status
) {
}
