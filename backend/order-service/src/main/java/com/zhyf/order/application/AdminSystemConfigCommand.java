package com.zhyf.order.application;

public record AdminSystemConfigCommand(
        String configKey,
        String configName,
        String configValue,
        String valueType,
        Boolean enabled,
        String remark
) {
}
