package com.zhyf.order.application;

import java.time.Instant;

public record AdminManualProcessCommand(
        String operator,
        String auditor,
        Instant auditTime,
        String dispenser,
        Instant dispenseTime,
        String rechecker,
        Instant recheckTime,
        String pailNo,
        Instant soakTimeStart,
        Instant boilTimeStart,
        Instant outboundTime,
        Instant signTime,
        String remark
) {
}
