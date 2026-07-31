package com.zhyf.order.application;

import java.time.Instant;

public record LegacyPdaLogisticsOutboundCommand(
        String recipeId,
        String operFlag,
        String operator,
        Instant outboundTime
) {
}
