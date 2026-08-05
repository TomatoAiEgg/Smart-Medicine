package com.zhyf.authinstitution.app;

import com.zhyf.authinstitution.config.AdminAuthProperties;
import com.zhyf.authinstitution.infrastructure.AdminAuthRepository;
import com.zhyf.authinstitution.infrastructure.AdminAuthRepository.AdminAccount;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.security.AdminJwtCodec;
import com.zhyf.common.security.AdminJwtCodec.IssuedAdminToken;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminAuthService {

    private final AdminAuthRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwtCodec jwtCodec;
    private final AdminAuthProperties properties;

    public AdminAuthService(
            AdminAuthRepository repository,
            PasswordEncoder passwordEncoder,
            AdminJwtCodec jwtCodec,
            AdminAuthProperties properties
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtCodec = jwtCodec;
        this.properties = properties;
    }

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
        List<java.util.UUID> institutionIds = repository.findInstitutionIds(account.tenantId(), account.userId());
        List<String> permissions = repository.findPermissions(account.tenantId(), account.userId());
        boolean tenantWide = repository.hasTenantWideDataScope(account.tenantId(), account.userId());
        IssuedAdminToken issued = jwtCodec.issue(
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
                Duration.ofSeconds(properties.getAccessTokenSeconds())
        );
        repository.recordLoginSuccess(account.userId());
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
                "Bearer",
                issued.principal().expiresAt(),
                new AdminAuthRecords.AdminUserView(
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
                )
        );
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
