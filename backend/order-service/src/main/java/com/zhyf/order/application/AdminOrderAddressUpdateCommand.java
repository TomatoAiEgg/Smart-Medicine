package com.zhyf.order.application;

public record AdminOrderAddressUpdateCommand(
        String receiverName,
        String receiverPhone,
        String receiverProvince,
        String receiverCity,
        String receiverZone,
        String receiverAddress,
        String addressType,
        String deliveryTime,
        String operator,
        String reason
) {
}
