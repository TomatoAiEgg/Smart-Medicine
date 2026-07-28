package com.zhyf.order.application;

public record AdminHerbAreaCommand(
        String areaCode,
        String areaName,
        Boolean enabled,
        String remark
) {
}
