package com.zhyf.order.application;

public record AdminInstitutionApiCommand(
        String apiCode,
        String apiName,
        String requestMethod,
        String requestPath,
        String description,
        Boolean enabled
) {
}
