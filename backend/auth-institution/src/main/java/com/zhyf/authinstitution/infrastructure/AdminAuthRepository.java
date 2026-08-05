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
}
