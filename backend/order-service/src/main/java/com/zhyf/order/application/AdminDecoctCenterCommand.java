package com.zhyf.order.application;

public record AdminDecoctCenterCommand(
        String centerCode,
        String centerName,
        String contactName,
        String contactPhone,
        String address,
        Boolean enabled,
        String remark
) {
}
