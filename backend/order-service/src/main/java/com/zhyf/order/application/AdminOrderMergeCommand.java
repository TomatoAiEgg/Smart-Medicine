package com.zhyf.order.application;

import java.util.List;

public record AdminOrderMergeCommand(
        List<String> orderNos,
        String logisticsCompany,
        String logisticsNo,
        String remark
) {
}
