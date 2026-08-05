package com.zhyf.authinstitution.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAuthRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminAuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AdminAccount> findAccount(String tenantCode, String username) {
        String sql = """
                select u.id, u.tenant_id, t.tenant_code, t.tenant_name, u.username, u.display_name,
                       u.password_hash, u.password_algorithm, u.credential_status, u.enabled,
                       u.login_failed_count, u.locked_until
                from operator_user u
                join tenant t on t.id = u.tenant_id
                where lower(t.tenant_code) = lower(?)
                  and lower(u.username) = lower(?)
                  and t.status = 'ENABLED'
                """;
        return jdbcTemplate.query(sql, this::mapAccount, tenantCode, username).stream().findFirst();
    }

    public Optional<AdminAccount> findAccount(UUID tenantId, UUID userId) {
        String sql = """
                select u.id, u.tenant_id, t.tenant_code, t.tenant_name, u.username, u.display_name,
                       u.password_hash, u.password_algorithm, u.credential_status, u.enabled,
                       u.login_failed_count, u.locked_until
                from operator_user u
                join tenant t on t.id = u.tenant_id
                where u.tenant_id = ?
                  and u.id = ?
                  and t.status = 'ENABLED'
                """;
        return jdbcTemplate.query(sql, this::mapAccount, tenantId, userId).stream().findFirst();
    }

    public List<String> findRoleCodes(UUID tenantId, UUID userId) {
        String sql = """
                select r.role_code
                from admin_user_role ur
                join admin_role r on r.tenant_id = ur.tenant_id and r.id = ur.role_id
                where ur.tenant_id = ? and ur.user_id = ? and r.enabled = true
                order by r.role_code
                """;
        return jdbcTemplate.queryForList(sql, String.class, tenantId, userId);
    }

    public List<UUID> findInstitutionIds(UUID tenantId, UUID userId) {
        String sql = """
                select institution_id
                from (
                    select s.institution_id
                    from admin_user_institution_scope s
                    where s.tenant_id = ? and s.user_id = ?
                    union
                    select s.institution_id
                    from admin_user_role ur
                    join admin_role_institution_scope s
                      on s.tenant_id = ur.tenant_id and s.role_id = ur.role_id
                    where ur.tenant_id = ? and ur.user_id = ?
                ) scoped
                order by institution_id
                """;
        return jdbcTemplate.queryForList(sql, UUID.class, tenantId, userId, tenantId, userId);
    }

    public List<String> findPermissions(UUID tenantId, UUID userId) {
        String sql = """
                select distinct p.permission_code
                from admin_user_role ur
                join admin_role r on r.tenant_id = ur.tenant_id and r.id = ur.role_id and r.enabled = true
                join admin_role_permission rp on rp.tenant_id = ur.tenant_id and rp.role_id = ur.role_id
                join admin_permission p on p.id = rp.permission_id and p.enabled = true
                where ur.tenant_id = ? and ur.user_id = ?
                order by p.permission_code
                """;
        return jdbcTemplate.queryForList(sql, String.class, tenantId, userId);
    }

    public boolean hasTenantWideDataScope(UUID tenantId, UUID userId) {
        String sql = """
                select exists (
                    select 1
                    from admin_user_role ur
                    join admin_role r
                      on r.tenant_id = ur.tenant_id and r.id = ur.role_id
                    where ur.tenant_id = ?
                      and ur.user_id = ?
                      and r.enabled = true
                      and r.data_scope_type = 'TENANT'
                )
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, tenantId, userId));
    }

    public int recordLoginFailure(UUID userId, int maxFailures, long lockSeconds) {
        String sql = """
                update operator_user
                set login_failed_count = login_failed_count + 1,
                    locked_until = case
                        when login_failed_count + 1 >= ? then now() + (? * interval '1 second')
                        else locked_until
                    end,
                    updated_at = now(),
                    version = version + 1
                where id = ?
                returning login_failed_count
                """;
        return jdbcTemplate.queryForObject(sql, Integer.class, maxFailures, lockSeconds, userId);
    }

    public void recordLoginSuccess(UUID userId) {
        String sql = """
                update operator_user
                set login_failed_count = 0,
                    locked_until = null,
                    last_login_at = now(),
                    updated_at = now(),
                    version = version + 1
                where id = ?
                """;
        jdbcTemplate.update(sql, userId);
    }

    public void writeLoginAudit(
            UUID tenantId,
            String username,
            String result,
            String reason,
            String requestId,
            String clientIp
    ) {
        String sql = """
                insert into operation_log (
                    id, tenant_id, operator, action, result, reason, request_id, client_ip, payload
                ) values (?, ?, ?, 'ADMIN_LOGIN', ?, ?, ?, ?, jsonb_build_object('username', ?))
                """;
        jdbcTemplate.update(
                sql,
                UUID.randomUUID(),
                tenantId,
                username,
                result,
                reason,
                requestId,
                clientIp,
                username
        );
    }

    public void createSession(
            UUID sessionId,
            UUID tenantId,
            UUID userId,
            String refreshTokenHash,
            Instant accessExpiresAt,
            Instant refreshExpiresAt
    ) {
        String sql = """
                insert into admin_auth_session (
                    id, tenant_id, user_id, refresh_token_hash, status,
                    access_expires_at, refresh_expires_at, last_rotated_at
                ) values (?, ?, ?, ?, 'ACTIVE', ?, ?, now())
                """;
        jdbcTemplate.update(
                sql,
                sessionId,
                tenantId,
                userId,
                refreshTokenHash,
                OffsetDateTime.ofInstant(accessExpiresAt, java.time.ZoneOffset.UTC),
                OffsetDateTime.ofInstant(refreshExpiresAt, java.time.ZoneOffset.UTC)
        );
    }

    public Optional<AdminSession> findSessionForUpdate(UUID sessionId) {
        String sql = """
                select id, tenant_id, user_id, refresh_token_hash, status,
                       access_expires_at, refresh_expires_at, revoked_at, revoke_reason
                from admin_auth_session
                where id = ?
                for update
                """;
        return jdbcTemplate.query(sql, this::mapSession, sessionId).stream().findFirst();
    }

    public int rotateSession(
            UUID sessionId,
            String expectedRefreshTokenHash,
            String nextRefreshTokenHash,
            Instant accessExpiresAt
    ) {
        String sql = """
                update admin_auth_session
                set refresh_token_hash = ?,
                    access_expires_at = ?,
                    last_rotated_at = now(),
                    updated_at = now(),
                    version = version + 1
                where id = ?
                  and status = 'ACTIVE'
                  and refresh_token_hash = ?
                  and refresh_expires_at > now()
                """;
        return jdbcTemplate.update(
                sql,
                nextRefreshTokenHash,
                OffsetDateTime.ofInstant(accessExpiresAt, java.time.ZoneOffset.UTC),
                sessionId,
                expectedRefreshTokenHash
        );
    }

    public int revokeSession(UUID sessionId, String reason) {
        String sql = """
                update admin_auth_session
                set status = 'REVOKED',
                    revoked_at = coalesce(revoked_at, now()),
                    revoke_reason = ?,
                    updated_at = now(),
                    version = version + 1
                where id = ? and status = 'ACTIVE'
                """;
        return jdbcTemplate.update(sql, reason, sessionId);
    }

    public void writeSessionAudit(
            UUID tenantId,
            String username,
            String action,
            String result,
            String reason,
            UUID sessionId,
            String requestId,
            String clientIp
    ) {
        String sql = """
                insert into operation_log (
                    id, tenant_id, operator, action, result, reason, request_id, client_ip, payload
                ) values (?, ?, ?, ?, ?, ?, ?, ?, jsonb_build_object('sessionId', ?::text))
                """;
        jdbcTemplate.update(
                sql,
                UUID.randomUUID(),
                tenantId,
                username,
                action,
                result,
                reason,
                requestId,
                clientIp,
                sessionId
        );
    }

    private AdminAccount mapAccount(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime lockedUntil = rs.getObject("locked_until", OffsetDateTime.class);
        return new AdminAccount(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("tenant_code"),
                rs.getString("tenant_name"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                rs.getString("password_algorithm"),
                rs.getString("credential_status"),
                rs.getBoolean("enabled"),
                rs.getInt("login_failed_count"),
                lockedUntil == null ? null : lockedUntil.toInstant()
        );
    }

    private AdminSession mapSession(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime accessExpiresAt = rs.getObject("access_expires_at", OffsetDateTime.class);
        OffsetDateTime refreshExpiresAt = rs.getObject("refresh_expires_at", OffsetDateTime.class);
        OffsetDateTime revokedAt = rs.getObject("revoked_at", OffsetDateTime.class);
        return new AdminSession(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("refresh_token_hash"),
                rs.getString("status"),
                accessExpiresAt.toInstant(),
                refreshExpiresAt.toInstant(),
                revokedAt == null ? null : revokedAt.toInstant(),
                rs.getString("revoke_reason")
        );
    }

    public record AdminAccount(
            UUID userId,
            UUID tenantId,
            String tenantCode,
            String tenantName,
            String username,
            String displayName,
            String passwordHash,
            String passwordAlgorithm,
            String credentialStatus,
            boolean enabled,
            int loginFailedCount,
            Instant lockedUntil
    ) {
    }

    public record AdminSession(
            UUID sessionId,
            UUID tenantId,
            UUID userId,
            String refreshTokenHash,
            String status,
            Instant accessExpiresAt,
            Instant refreshExpiresAt,
            Instant revokedAt,
            String revokeReason
    ) {
    }
}
