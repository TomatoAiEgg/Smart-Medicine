package com.zhyf.order.application;

import java.time.Instant;

public record AdminPrescriptionReprintQuery(
        Instant startTime,
        Instant endTime,
        String prescriptionNo,
        int page,
        int pageSize
) {
}
