package com.zhyf.order.application;

public record AdminOrderRemarkUpdateCommand(
        String remark,
        String operator,
        String reason
) {
}
