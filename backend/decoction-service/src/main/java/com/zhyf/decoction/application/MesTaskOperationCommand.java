package com.zhyf.decoction.application;

import java.time.Instant;

public record MesTaskOperationCommand(
        String operationId,
        String operator,
        Instant timestamp,
        String sign
) {
}
