package com.zhyf.order.application;

import java.time.Instant;

public record AdminOrderRecheckQuery(
        Instant startTime,
        Instant endTime,
        String institution,
        String prescriptionType,
        String hospitalType,
        Integer isWithin,
        String deliveryType,
        String recheckStatus,
        String batchNo,
        String prescriptionNo,
        String dispenser,
        String rechecker,
        int page,
        int pageSize
) {
}
