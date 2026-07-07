package com.zhyf.decoction.domain;

import java.util.UUID;

public record PrescriptionForDecoction(
        UUID tenantId,
        UUID orderId,
        UUID prescriptionId,
        String orderNo,
        String externalOrderNo,
        String prescriptionNo,
        String orderStatus
) {
}
