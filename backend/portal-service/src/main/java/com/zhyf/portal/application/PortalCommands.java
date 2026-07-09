package com.zhyf.portal.application;

public final class PortalCommands {

    private PortalCommands() {
    }

    public record PortalOrderQuery(
            String orderNo,
            String externalOrderNo,
            String phone
    ) {
    }

    public record AddressSupplementCommand(
            String phone,
            String receiverName,
            String receiverPhone,
            String receiverProvince,
            String receiverCity,
            String receiverZone,
            String receiverAddress,
            String requesterName,
            String requesterPhone,
            String remark
    ) {
    }
}
