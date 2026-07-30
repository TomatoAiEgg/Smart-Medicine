package com.zhyf.decoction.application;

public record DecoctionDeviceCommand(
        String deviceCode,
        String deviceName,
        String deviceType,
        String deviceGroup,
        String decoctionCenter,
        String pdaCode,
        String printerCode,
        String printTemplateCode,
        Boolean enabled,
        String remark
) {
}
