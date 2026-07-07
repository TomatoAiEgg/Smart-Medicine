package com.zhyf.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SignatureUtilsTest {

    @Test
    void hmacSha256HexShouldBeStable() {
        String signature = SignatureUtils.hmacSha256Hex("demo-secret", "demo-app\n1000\nabc");

        assertThat(signature).hasSize(64);
        assertThat(SignatureUtils.constantTimeEquals(signature, signature)).isTrue();
        assertThat(SignatureUtils.constantTimeEquals(signature, "bad")).isFalse();
    }
}
