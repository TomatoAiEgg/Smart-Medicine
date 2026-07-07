package com.zhyf.gateway.signature;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.security.SignatureUtils;
import com.zhyf.gateway.app.InstitutionAppView;
import com.zhyf.gateway.config.GatewayProperties;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SignatureVerifier {

    private final GatewayProperties properties;
    private final Clock clock;

    @Autowired
    public SignatureVerifier(GatewayProperties properties) {
        this(properties, Clock.systemUTC());
    }

    SignatureVerifier(GatewayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void verify(InstitutionAppView app, String appKey, String timestamp, String signature, String rawBody) {
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            throw new BusinessException("SIGN_HEADER_REQUIRED", "签名请求头不能为空");
        }
        verifyTimestamp(timestamp);
        String bodyHash = SignatureUtils.sha256Hex(rawBody);
        String source = appKey + "\n" + timestamp + "\n" + bodyHash;
        String expected = SignatureUtils.hmacSha256Hex(app.appSecret(), source);
        if (!SignatureUtils.constantTimeEquals(expected, signature)) {
            throw new BusinessException("INVALID_SIGNATURE", "签名错误");
        }
    }

    private void verifyTimestamp(String timestamp) {
        long requestMillis;
        try {
            requestMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw new BusinessException("INVALID_TIMESTAMP", "时间戳格式错误");
        }
        long diffMillis = Math.abs(clock.millis() - requestMillis);
        if (diffMillis > properties.signatureTimeoutSeconds() * 1000) {
            throw new BusinessException("SIGNATURE_EXPIRED", "签名已过期");
        }
    }
}
