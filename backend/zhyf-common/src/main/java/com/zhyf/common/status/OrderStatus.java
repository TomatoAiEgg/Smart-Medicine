package com.zhyf.common.status;

public enum OrderStatus {
    CREATED(5, "已接单"),
    AUDIT_PASSED(15, "审核通过"),
    RECHECKED(20, "复核完成"),
    DECOCTING(25, "煎煮中"),
    DECOCTED(27, "煎煮完成"),
    PACKED(30, "打包完成"),
    SHIPPED(36, "已发货"),
    IN_TRANSIT(37, "运输中"),
    SIGNED(40, "已签收"),
    AUDIT_FAILED(98, "审核失败"),
    CANCELLED(99, "已取消");

    private final int legacyCode;
    private final String description;

    OrderStatus(int legacyCode, String description) {
        this.legacyCode = legacyCode;
        this.description = description;
    }

    public int legacyCode() {
        return legacyCode;
    }

    public String description() {
        return description;
    }
}
