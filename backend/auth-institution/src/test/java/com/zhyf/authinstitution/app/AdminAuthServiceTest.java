package com.zhyf.authinstitution.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.authinstitution.app.AdminRefreshTokenCodec.IssuedRefreshToken;
import com.zhyf.authinstitution.config.AdminAuthProperties;
import com.zhyf.authinstitution.infrastructure.AdminAuthRepository;
import com.zhyf.authinstitution.infrastructure.AdminAuthRepository.AdminAccount;
import com.zhyf.authinstitution.infrastructure.AdminAuthRepository.AdminSession;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.security.AdminJwtCodec;
import com.zhyf.common.security.AdminPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminAuthServiceTest {

    private final AdminAuthRepository repository = mock(AdminAuthRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminJwtCodec jwtCodec = mock(AdminJwtCodec.class);
    private final AdminRefreshTokenCodec refreshTokenCodec = mock(AdminRefreshTokenCodec.class);
    private final AdminAuthProperties properties = new AdminAuthProperties();
    private final AdminAuthService service = new AdminAuthService(
            repository,
            passwordEncoder,
            jwtCodec,
            refreshTokenCodec,
            properties
    );

    private UUID userId;
    private UUID tenantId;
    private AdminAccount account;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        account = new AdminAccount(
                userId,
                tenantId,
                "tenant-a",
                "租户 A",
                "admin",
                "管理员",
                "bcrypt-hash",
                "BCRYPT",
                "ACTIVE",
                true,
                0,
                null
        );
        when(repository.findRoleCodes(tenantId, userId)).thenReturn(List.of("TENANT_ADMIN"));
        when(repository.findInstitutionIds(tenantId, userId)).thenReturn(List.of());
        when(repository.findPermissions(tenantId, userId)).thenReturn(List.of("system:write"));
        when(repository.hasTenantWideDataScope(tenantId, userId)).thenReturn(true);
    }

    @Test
    void shouldCreateSessionWithRefreshTokenHashOnLogin() {
        when(repository.findAccount("tenant-a", "admin")).thenReturn(java.util.Optional.of(account));
        when(passwordEncoder.matches("password", "bcrypt-hash")).thenReturn(true);
        when(refreshTokenCodec.issue(any(UUID.class))).thenAnswer(invocation -> {
            UUID sessionId = invocation.getArgument(0);
            return new IssuedRefreshToken(sessionId, sessionId + ".secret", "hashed-refresh-token");
        });
        mockAccessTokenIssue();

        AdminAuthRecords.LoginResult result = service.login(
                new AdminAuthRecords.LoginCommand("tenant-a", "admin", "password", "req-1", "127.0.0.1")
        );

        assertThat(result.refreshToken()).endsWith(".secret");
        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(repository).createSession(
                any(UUID.class),
                eq(tenantId),
                eq(userId),
                eq("hashed-refresh-token"),
                any(Instant.class),
                any(Instant.class)
        );
    }

    @Test
    void shouldRevokeSessionWhenRotatedRefreshTokenIsReplayed() {
        UUID sessionId = UUID.randomUUID();
        AdminSession session = new AdminSession(
                sessionId,
                tenantId,
                userId,
                "current-hash",
                "ACTIVE",
                Instant.now().plusSeconds(600),
                Instant.now().plusSeconds(3600),
                null,
                null
        );
        when(refreshTokenCodec.sessionId("old-token")).thenReturn(sessionId);
        when(repository.findSessionForUpdate(sessionId)).thenReturn(java.util.Optional.of(session));
        when(refreshTokenCodec.matches("old-token", "current-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.refresh(
                new AdminAuthRecords.RefreshCommand("old-token", "req-2", "127.0.0.1")
        )).isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.code()).isEqualTo("ADMIN_REFRESH_TOKEN_INVALID")
        );

        verify(repository).revokeSession(sessionId, "REFRESH_TOKEN_REUSED");
        verify(repository).writeSessionAudit(
                eq(tenantId),
                anyString(),
                eq("ADMIN_TOKEN_REFRESH"),
                eq("FAILED"),
                eq("REFRESH_TOKEN_REUSED"),
                eq(sessionId),
                eq("req-2"),
                eq("127.0.0.1")
        );
    }

    private void mockAccessTokenIssue() {
        when(jwtCodec.issue(
                eq(userId),
                eq(tenantId),
                eq("tenant-a"),
                eq("租户 A"),
                eq("admin"),
                eq("管理员"),
                eq(List.of("TENANT_ADMIN")),
                eq(List.of()),
                eq(List.of("system:write")),
                eq(true),
                any(UUID.class),
                eq(Duration.ofSeconds(properties.getAccessTokenSeconds()))
        )).thenAnswer(invocation -> {
            UUID sessionId = invocation.getArgument(10);
            Instant issuedAt = Instant.now();
            AdminPrincipal principal = new AdminPrincipal(
                    userId,
                    tenantId,
                    "tenant-a",
                    "租户 A",
                    "admin",
                    "管理员",
                    List.of("TENANT_ADMIN"),
                    List.of(),
                    List.of("system:write"),
                    true,
                    issuedAt,
                    issuedAt.plusSeconds(properties.getAccessTokenSeconds()),
                    sessionId,
                    UUID.randomUUID().toString()
            );
            return new AdminJwtCodec.IssuedAdminToken("access-token", principal);
        });
    }
}
