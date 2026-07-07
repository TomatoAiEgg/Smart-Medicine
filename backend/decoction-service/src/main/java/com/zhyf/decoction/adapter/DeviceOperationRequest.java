package com.zhyf.decoction.adapter;

import java.time.Instant;

public record DeviceOperationRequest(
        String operationId,
        String taskNo,
        String deviceCode,
        String prescriptionNo,
        String pailNo,
        String operator,
        Instant timestamp,
        String sign,
        String status,
        String reason,
        Integer waterVolumeMl,
        Integer temperatureCelsius,
        Integer durationSeconds,
        String remark
) {
}
