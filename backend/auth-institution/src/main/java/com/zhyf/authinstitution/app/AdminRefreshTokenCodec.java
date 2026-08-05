package com.zhyf.authinstitution.app;

import com.zhyf.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminRefreshTokenCodec {

    private static final int SECRET_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 256;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom secureRandom = new SecureRandom();

    public IssuedRefreshToken issue(UUID sessionId) {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        String token = sessionId + "." + URL_ENCODER.encodeToString(secret);
        return new IssuedRefreshToken(sessionId, token, hash(token));
    }

    public UUID sessionId(String token) {
        String normalized = normalized(token);
        int separator = normalized.indexOf('.');
        if (separator <= 0 || separator == normalized.length() - 1) {
            throw invalidToken();
        }
        try {
            return UUID.fromString(normalized.substring(0, separator));
        } catch (IllegalArgumentException ex) {
            throw invalidToken();
        }
    }

    public String hash(String token) {
        String normalized = normalized(token);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 不支持 SHA-256", ex);
        }
    }

    public boolean matches(String token, String expectedHash) {
        if (!StringUtils.hasText(expectedHash)) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(token).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private String normalized(String token) {
        if (!StringUtils.hasText(token) || token.length() > MAX_TOKEN_LENGTH) {
            throw invalidToken();
        }
        return token.trim();
    }

    private BusinessException invalidToken() {
        return new BusinessException("ADMIN_REFRESH_TOKEN_INVALID", "刷新令牌无效，请重新登录");
    }

    public record IssuedRefreshToken(UUID sessionId, String token, String hash) {
    }
}
