package com.zhyf.order.application;

import com.fasterxml.jackson.databind.JsonNode;

public record OrderCreateCommand(
        String appKey,
        String timestamp,
        String signature,
        String requestIp,
        JsonNode payload
) {
}
