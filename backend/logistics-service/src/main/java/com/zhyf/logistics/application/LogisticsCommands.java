package com.zhyf.logistics.application;

import java.math.BigDecimal;
import java.time.Instant;

public final class LogisticsCommands {

    private LogisticsCommands() {
    }

    public record PackCommand(
            String orderNo,
            String logisticsCompany,
            String logisticsNo,
            String payMethod,
            BigDecimal pkgWeight,
            Integer pkgNum,
            String operator
    ) {
    }

    public record ShipmentActionCommand(
            String operator,
            String remark
    ) {
    }

    public record TraceCommand(
            String logisticsNo,
            String provider,
            String opCode,
            String traceContent,
            String rawPayload,
            Instant traceTime,
            String operator
    ) {
    }
}
