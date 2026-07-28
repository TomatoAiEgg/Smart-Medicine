package com.zhyf.order.application;

public record AdminDictTypeCommand(
        String typeCode,
        String typeName,
        Boolean enabled
) {
}
