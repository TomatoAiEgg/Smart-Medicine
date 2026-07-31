package com.zhyf.order.application;

public record LegacyPdaLabelPrintInfo(
        String serialNo,
        String param,
        String paramName,
        String value
) {
}
