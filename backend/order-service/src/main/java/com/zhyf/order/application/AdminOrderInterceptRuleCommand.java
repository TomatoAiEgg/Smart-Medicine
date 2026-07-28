package com.zhyf.order.application;

public record AdminOrderInterceptRuleCommand(
        String ruleCode,
        String ruleName,
        String interceptStage,
        String matchField,
        String matchType,
        String matchValue,
        String reason,
        Integer priority,
        Boolean enabled
) {
}
