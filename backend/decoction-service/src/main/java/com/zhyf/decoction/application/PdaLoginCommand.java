package com.zhyf.decoction.application;

public record PdaLoginCommand(
        String operator,
        String deviceCode,
        String password
) {
}
