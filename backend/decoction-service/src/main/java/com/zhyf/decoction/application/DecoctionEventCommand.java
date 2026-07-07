package com.zhyf.decoction.application;

import java.time.Instant;

public record DecoctionEventCommand(
        String operationId,
        String operator,
        Instant timestamp,
        String sign,
        String reason,
        Integer waterVolumeMl,
        Integer temperatureCelsius,
        Integer durationSeconds,
        String remark
) {
}
