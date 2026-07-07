package com.zhyf.decoction.application;

import java.time.Instant;

public record SimulatorOperationCommand(
        String operationId,
        String deviceCode,
        String prescriptionNo,
        String pailNo,
        String operator,
        Instant timestamp,
        String sign
) {
}
