package com.zhyf.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class AdminJwtCodec {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final byte[] secret;
    private final String issuer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminJwtCodec(String secret, String issuer, ObjectMapper objectMapper) {
        this(secret, issuer, objectMapper, Clock.systemUTC());
    }

    AdminJwtCodec(String secret, String issuer, ObjectMapper objectMapper, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("管理员令牌密钥长度不能少于 32 字节");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("管理员令牌签发方不能为空");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer.trim();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public IssuedAdminToken issue(
            UUID userId,
            UUID tenantId,
            String tenantCode,
            String tenantName,
            String username,
            String displayName,
            List<String> roleCodes,
            List<UUID> institutionIds,
            List<String> permissions,
            boolean tenantWide,
            UUID sessionId,
            Duration ttl
    ) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("管理员令牌有效期必须大于 0");
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("管理员会话 ID 不能为空");
        }
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(ttl);
        AdminPrincipal principal = new AdminPrincipal(
                userId,
                tenantId,
                tenantCode,
                tenantName,
                username,
                displayName,
                roleCodes,
                institutionIds,
                permissions,
                tenantWide,
                issuedAt,
                expiresAt,
                sessionId,
                UUID.randomUUID().toString()
        );
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", principal.userId().toString());
        payload.put("tenantId", principal.tenantId().toString());
        payload.put("tenantCode", principal.tenantCode());
        payload.put("tenantName", principal.tenantName());
        payload.put("username", principal.username());
        payload.put("displayName", principal.displayName());
        payload.put("roles", principal.roleCodes());
        payload.put("institutionIds", principal.institutionIds().stream().map(UUID::toString).toList());
        payload.put("permissions", principal.permissions());
        payload.put("tenantWide", principal.tenantWide());
        payload.put("iat", principal.issuedAt().getEpochSecond());
        payload.put("exp", principal.expiresAt().getEpochSecond());
        payload.put("sid", principal.sessionId().toString());
        payload.put("jti", principal.tokenId());
        String signingInput = encode(header) + "." + encode(payload);
        return new IssuedAdminToken(signingInput + "." + sign(signingInput), principal);
    }

    public AdminPrincipal verify(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 3) {
                throw new AdminTokenException("管理员令牌格式错误");
            }
            byte[] expected = URL_DECODER.decode(sign(parts[0] + "." + parts[1]));
            byte[] actual = URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new AdminTokenException("管理员令牌签名无效");
            }
            Map<String, Object> header = decode(parts[0]);
            if (!"HS256".equals(text(header, "alg")) || !"JWT".equals(text(header, "typ"))) {
                throw new AdminTokenException("管理员令牌算法无效");
            }
            Map<String, Object> payload = decode(parts[1]);
            if (!issuer.equals(text(payload, "iss"))) {
                throw new AdminTokenException("管理员令牌签发方无效");
            }
            Instant expiresAt = Instant.ofEpochSecond(number(payload, "exp"));
            if (!expiresAt.isAfter(Instant.now(clock))) {
                throw new AdminTokenException("管理员登录已过期");
            }
            return new AdminPrincipal(
                    UUID.fromString(text(payload, "sub")),
                    UUID.fromString(text(payload, "tenantId")),
                    text(payload, "tenantCode"),
                    text(payload, "tenantName"),
                    text(payload, "username"),
                    text(payload, "displayName"),
                    textList(payload, "roles"),
                    textList(payload, "institutionIds").stream().map(UUID::fromString).toList(),
                    textList(payload, "permissions"),
                    bool(payload, "tenantWide"),
                    Instant.ofEpochSecond(number(payload, "iat")),
                    expiresAt,
                    UUID.fromString(text(payload, "sid")),
                    text(payload, "jti")
            );
        } catch (AdminTokenException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AdminTokenException("管理员令牌无法解析", ex);
        }
    }

    private String encode(Object value) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException ex) {
            throw new AdminTokenException("管理员令牌序列化失败", ex);
        }
    }

    private Map<String, Object> decode(String value) {
        try {
            return objectMapper.readValue(URL_DECODER.decode(value), MAP_TYPE);
        } catch (Exception ex) {
            throw new AdminTokenException("管理员令牌载荷无效", ex);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new AdminTokenException("管理员令牌签名失败", ex);
        }
    }

    private String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new AdminTokenException("管理员令牌缺少字段: " + key);
        }
        return text;
    }

    private long number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw new AdminTokenException("管理员令牌缺少字段: " + key);
        }
        return number.longValue();
    }

    private List<String> textList(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private boolean bool(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Boolean bool)) {
            throw new AdminTokenException("管理端令牌缺少字段: " + key);
        }
        return bool;
    }

    public record IssuedAdminToken(String token, AdminPrincipal principal) {
    }
}
