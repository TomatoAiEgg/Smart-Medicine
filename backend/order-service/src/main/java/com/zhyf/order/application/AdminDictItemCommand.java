package com.zhyf.order.application;

import java.util.UUID;

public record AdminDictItemCommand(
        UUID typeId,
        String itemCode,
        String itemName,
        String itemValue,
        Integer sortNo,
        Boolean enabled,
        String remark
) {
}
