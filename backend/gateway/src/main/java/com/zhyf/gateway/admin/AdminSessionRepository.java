package com.zhyf.gateway.admin;

import com.zhyf.common.security.AdminPrincipal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isActive(AdminPrincipal principal) {
        String sql = """
                select exists (
                    select 1
                    from admin_auth_session
                    where id = ?
                      and tenant_id = ?
                      and user_id = ?
                      and status = 'ACTIVE'
                      and access_expires_at > now()
                      and refresh_expires_at > now()
                )
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                principal.sessionId(),
                principal.tenantId(),
                principal.userId()
        ));
    }

    @Transactional
    public int revokeCurrentSession(
            AdminPrincipal principal,
            String requestId,
            String clientIp
    ) {
        int revoked = revokeSessions(
                "id = ? and tenant_id = ? and user_id = ?",
                "USER_LOGOUT",
                principal.sessionId(),
                principal.tenantId(),
                principal.userId()
        );
        writeAudit(
                principal,
                "ADMIN_LOGOUT",
                principal.userId(),
                revoked,
                requestId,
                clientIp
        );
        return revoked;
    }

    @Transactional
    public int revokeUserSessions(
            AdminPrincipal principal,
            UUID targetUserId,
            String requestId,
            String clientIp
    ) {
        int revoked = revokeSessions(
                "tenant_id = ? and user_id = ?",
                "ADMIN_FORCE_LOGOUT",
                principal.tenantId(),
                targetUserId
        );
        writeAudit(
                principal,
                "ADMIN_FORCE_LOGOUT",
                targetUserId,
                revoked,
                requestId,
                clientIp
        );
        return revoked;
    }

    private int revokeSessions(String predicate, String reason, Object... args) {
        String sql = """
                update admin_auth_session
                set status = 'REVOKED',
                    revoked_at = coalesce(revoked_at, now()),
                    revoke_reason = ?,
                    updated_at = now(),
                    version = version + 1
                where status = 'ACTIVE' and
                """ + predicate;
        Object[] parameters = new Object[args.length + 1];
        parameters[0] = reason;
        System.arraycopy(args, 0, parameters, 1, args.length);
        return jdbcTemplate.update(sql, parameters);
    }

    private void writeAudit(
            AdminPrincipal principal,
            String action,
            UUID targetUserId,
            int revokedSessions,
            String requestId,
            String clientIp
    ) {
        String sql = """
                insert into operation_log (
                    id, tenant_id, operator, action, result, request_id, client_ip, payload
                ) values (
                    ?, ?, ?, ?, 'SUCCESS', ?, ?,
                    jsonb_build_object(
                        'sessionId', cast(? as text),
                        'targetUserId', cast(? as text),
                        'revokedSessions', ?
                    )
                )
                """;
        jdbcTemplate.update(
                sql,
                UUID.randomUUID(),
                principal.tenantId(),
                principal.username(),
                action,
                requestId,
                clientIp,
                principal.sessionId(),
                targetUserId,
                revokedSessions
        );
    }
}
