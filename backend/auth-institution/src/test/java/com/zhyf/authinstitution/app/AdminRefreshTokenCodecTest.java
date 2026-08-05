package com.zhyf.authinstitution.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhyf.common.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminRefreshTokenCodecTest {

    private final AdminRefreshTokenCodec codec = new AdminRefreshTokenCodec();

    @Test
    void shouldIssueOpaqueTokenAndKeepOnlyStableHash() {
        UUID sessionId = UUID.randomUUID();

        AdminRefreshTokenCodec.IssuedRefreshToken issued = codec.issue(sessionId);

        assertThat(issued.token()).startsWith(sessionId + ".");
        assertThat(issued.hash()).hasSize(64).doesNotContain(issued.token());
        assertThat(codec.sessionId(issued.token())).isEqualTo(sessionId);
        assertThat(codec.matches(issued.token(), issued.hash())).isTrue();
    }

    @Test
    void shouldRejectMalformedOrRotatedToken() {
        AdminRefreshTokenCodec.IssuedRefreshToken first = codec.issue(UUID.randomUUID());
        AdminRefreshTokenCodec.IssuedRefreshToken rotated = codec.issue(first.sessionId());

        assertThat(codec.matches(first.token(), rotated.hash())).isFalse();
        assertThatThrownBy(() -> codec.sessionId("invalid"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("刷新令牌无效，请重新登录");
    }
}
