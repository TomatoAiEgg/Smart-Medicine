package com.zhyf.order.application;

import java.math.BigDecimal;

public record AdminHerbCommand(
        String herbCode,
        String herbName,
        String drugSpecs,
        String drugOrigin,
        String unit,
        BigDecimal retailPrice,
        Boolean enabled,
        String remark
) {
}
