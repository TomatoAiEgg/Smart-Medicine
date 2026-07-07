package com.zhyf.callback.application;

import java.util.UUID;

public record CallbackCreateCommand(
        UUID orderId,
        String callbackType,
        String businessId,
        String businessStatus,
        String source
) {
}
