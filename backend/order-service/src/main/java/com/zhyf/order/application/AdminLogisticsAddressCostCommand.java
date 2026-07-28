package com.zhyf.order.application;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminLogisticsAddressCostCommand(
        UUID institutionId,
        String logisticsCompany,
        String province,
        String city,
        String district,
        BigDecimal costAmount,
        String remark,
        Boolean enabled
) {
}
