package com.zhyf.authinstitution.app;

import com.zhyf.authinstitution.config.AdminAuthProperties;
import com.zhyf.authinstitution.infrastructure.AdminAuthRepository;
import com.zhyf.authinstitution.infrastructure.AdminAuthRepository.AdminAccount;
import com.zhyf.authinstitution.infrastructure.AdminAuthRepository.AdminSession;
import com.zhyf.authinstitution.app.AdminRefreshTokenCodec.IssuedRefreshToken;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.security.AdminJwtCodec;
import com.zhyf.common.security.AdminJwtCodec.IssuedAdminToken;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAuthService {

    private final AdminAuthRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwtCodec jwtCodec;
    private final AdminRefreshTokenCodec refreshTokenCodec;
    private final AdminAuthProperties properties;

    public AdminAuthService(
            AdminAuthRepository repository,
            PasswordEncoder passwordEncoder,
            AdminJwtCodec jwtCodec,
            AdminRefreshTokenCodec refreshTokenCodec,
            AdminAuthProperties properties
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtCodec = jwtCodec;
        this.refreshTokenCodec = refreshTokenCodec;
        this.properties = properties;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AdminAuthRecords.LoginResult login(AdminAuthRecords.LoginCommand command) {
        String tenantCode = bounded(command.tenantCode(), 64, "TENANT_CODE_REQUIRED", "租户编码不能为空");
        String username = bounded(command.username(), 64, "USERNAME_REQUIRED", "用户名不能为空");
        String password = required(command.password(), "PASSWORD_REQUIRED", "密码不能为空");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException("ADMIN_LOGIN_FAILED", "租户、用户名或密码错误");
        }
        AdminAccount account = repository.findAccount(tenantCode, username)
                .orElseThrow(() -> new BusinessException("ADMIN_LOGIN_FAILED", "租户、用户名或密码错误"));
        validateAccount(account);
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            int failures = repository.recordLoginFailure(
                    account.userId(),
                    properties.getMaxLoginFailures(),
                    properties.getLockSeconds()
            );
            repository.writeLoginAudit(
                    account.tenantId(),
                    account.username(),
                    "FAILED",
                    "PASSWORD_MISMATCH:" + failures,
                    command.requestId(),
                    command.clientIp()
            );
            throw new BusinessException("ADMIN_LOGIN_FAILED", "租户、用户名或密码错误");
        }
        List<String> roleCodes = repository.findRoleCodes(account.tenantId(), account.userId());
        if (roleCodes.isEmpty()) {
            throw new BusinessException("ADMIN_ROLE_REQUIRED", "账号尚未分配角色");
        }
        List<UUID> institutionIds = repository.findInstitutionIds(account.tenantId(), account.userId());
        List<String> permissions = repository.findPermissions(account.tenantId(), account.userId());
        boolean tenantWide = repository.hasTenantWideDataScope(account.tenantId(), account.userId());
        UUID sessionId = UUID.randomUUID();
        IssuedRefreshToken refreshToken = refreshTokenCodec.issue(sessionId);
        Instant refreshExpiresAt = Instant.now().plusSeconds(properties.getRefreshTokenSeconds());
        IssuedAdminToken issued = issueAccessToken(
                account.userId(),
                account.tenantId(),
                account.tenantCode(),
                account.tenantName(),
                account.username(),
                account.displayName(),
                roleCodes,
                institutionIds,
                permissions,
                tenantWide,
                sessionId
        );
        repository.recordLoginSuccess(account.userId());
        repository.createSession(
                sessionId,
                account.tenantId(),
                account.userId(),
                refreshToken.hash(),
                issued.principal().expiresAt(),
                refreshExpiresAt
        );
        repository.writeLoginAudit(
                account.tenantId(),
                account.username(),
                "SUCCESS",
                null,
                command.requestId(),
                command.clientIp()
        );
        return new AdminAuthRecords.LoginResult(
                issued.token(),
                refreshToken.token(),
                "Bearer",
                issued.principal().expiresAt(),
                refreshExpiresAt,
                userView(account, roleCodes, institutionIds, permissions, tenantWide)
        );
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AdminAuthRecords.LoginResult refresh(AdminAuthRecords.RefreshCommand command) {
        String token = required(
                command.refreshToken(),
                "ADMIN_REFRESH_TOKEN_INVALID",
                "刷新令牌无效，请重新登录"
        );
        UUID sessionId = refreshTokenCodec.sessionId(token);
        AdminSession session = repository.findSessionForUpdate(sessionId)
                .orElseThrow(this::invalidRefreshToken);
        if (!"ACTIVE".equals(session.status())) {
            throw new BusinessException("ADMIN_SESSION_REVOKED", "登录会话已失效，请重新登录");
        }
        if (!session.refreshExpiresAt().isAfter(Instant.now())) {
            repository.revokeSession(sessionId, "REFRESH_TOKEN_EXPIRED");
            throw new BusinessException("ADMIN_REFRESH_TOKEN_EXPIRED", "登录会话已过期，请重新登录");
        }
        if (!refreshTokenCodec.matches(token, session.refreshTokenHash())) {
            repository.revokeSession(sessionId, "REFRESH_TOKEN_REUSED");
            repository.writeSessionAudit(
                    session.tenantId(),
                    "unknown",
                    "ADMIN_TOKEN_REFRESH",
                    "FAILED",
                    "REFRESH_TOKEN_REUSED",
                    sessionId,
                    command.requestId(),
                    command.clientIp()
            );
            throw invalidRefreshToken();
        }
        AdminAccount account = repository.findAccount(session.tenantId(), session.userId())
                .orElseThrow(() -> revokeInvalidSession(sessionId));
        if (!account.enabled() || !"ACTIVE".equals(account.credentialStatus())) {
            throw revokeInvalidSession(sessionId);
        }
        List<String> roleCodes = repository.findRoleCodes(account.tenantId(), account.userId());
        if (roleCodes.isEmpty()) {
            throw revokeInvalidSession(sessionId);
        }
        List<UUID> institutionIds = repository.findInstitutionIds(account.tenantId(), account.userId());
        List<String> permissions = repository.findPermissions(account.tenantId(), account.userId());
        boolean tenantWide = repository.hasTenantWideDataScope(account.tenantId(), account.userId());
        IssuedRefreshToken nextRefreshToken = refreshTokenCodec.issue(sessionId);
        IssuedAdminToken issued = issueAccessToken(
                account.userId(),
                account.tenantId(),
                account.tenantCode(),
                account.tenantName(),
                account.username(),
                account.displayName(),
                roleCodes,
                institutionIds,
                permissions,
                tenantWide,
                sessionId
        );
        if (repository.rotateSession(
                sessionId,
                session.refreshTokenHash(),
                nextRefreshToken.hash(),
                issued.principal().expiresAt()
        ) == 0) {
            throw invalidRefreshToken();
        }
        repository.writeSessionAudit(
                account.tenantId(),
                account.username(),
                "ADMIN_TOKEN_REFRESH",
                "SUCCESS",
                null,
                sessionId,
                command.requestId(),
                command.clientIp()
        );
        return new AdminAuthRecords.LoginResult(
                issued.token(),
                nextRefreshToken.token(),
                "Bearer",
                issued.principal().expiresAt(),
                session.refreshExpiresAt(),
                userView(account, roleCodes, institutionIds, permissions, tenantWide)
        );
    }

    private IssuedAdminToken issueAccessToken(
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
            UUID sessionId
    ) {
        return jwtCodec.issue(
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
                sessionId,
                Duration.ofSeconds(properties.getAccessTokenSeconds())
        );
    }

    private AdminAuthRecords.AdminUserView userView(
            AdminAccount account,
            List<String> roleCodes,
            List<UUID> institutionIds,
            List<String> permissions,
            boolean tenantWide
    ) {
        return new AdminAuthRecords.AdminUserView(
                account.userId(),
                account.tenantId(),
                account.tenantCode(),
                account.tenantName(),
                account.username(),
                account.displayName(),
                roleCodes,
                institutionIds,
                permissions,
                tenantWide
        );
    }

    private BusinessException revokeInvalidSession(UUID sessionId) {
        repository.revokeSession(sessionId, "ACCOUNT_AUTHORIZATION_INVALID");
        return new BusinessException("ADMIN_SESSION_REVOKED", "登录会话已失效，请重新登录");
    }

    private BusinessException invalidRefreshToken() {
        return new BusinessException("ADMIN_REFRESH_TOKEN_INVALID", "刷新令牌无效，请重新登录");
    }

    private void validateAccount(AdminAccount account) {
        if (!account.enabled()) {
            throw new BusinessException("ADMIN_ACCOUNT_DISABLED", "账号已停用");
        }
        if (!"ACTIVE".equals(account.credentialStatus())
                || !"BCRYPT".equals(account.passwordAlgorithm())
                || !StringUtils.hasText(account.passwordHash())) {
            throw new BusinessException("ADMIN_CREDENTIAL_NOT_READY", "账号密码尚未初始化");
        }
        if (account.lockedUntil() != null && account.lockedUntil().isAfter(Instant.now())) {
            throw new BusinessException("ADMIN_ACCOUNT_LOCKED", "登录失败次数过多，账号已临时锁定");
        }
    }

    private String required(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private String bounded(String value, int maxLength, String code, String message) {
        String normalized = required(value, code, message);
        if (normalized.length() > maxLength) {
            throw new BusinessException("ADMIN_LOGIN_FAILED", "租户、用户名或密码错误");
        }
        return normalized;
    }
}
