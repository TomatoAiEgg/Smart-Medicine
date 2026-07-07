package com.zhyf.authinstitution.infrastructure;

import com.zhyf.authinstitution.app.InstitutionAppView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InstitutionAppRepository {

    private final JdbcTemplate jdbcTemplate;

    public InstitutionAppRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<InstitutionAppView> findEnabledApp(String appKey) {
        String sql = """
                select ia.tenant_id,
                       ia.institution_id,
                       ia.app_key,
                       ia.app_secret,
                       ia.sign_type,
                       ia.callback_url,
                       ia.enabled
                  from institution_app ia
                 where ia.app_key = ?
                   and ia.enabled = true
                """;
        return jdbcTemplate.query(sql, this::mapApp, appKey).stream()
                .map(app -> new InstitutionAppView(
                        app.tenantId(),
                        app.institutionId(),
                        app.appKey(),
                        app.appSecret(),
                        app.signType(),
                        app.callbackUrl(),
                        app.enabled(),
                        findWhitelist(app.tenantId(), app.institutionId())
                ))
                .findFirst();
    }

    private List<String> findWhitelist(UUID tenantId, UUID institutionId) {
        String sql = """
                select ip_range
                  from institution_ip_whitelist
                 where tenant_id = ?
                   and institution_id = ?
                   and enabled = true
                 order by created_at asc
                """;
        return jdbcTemplate.queryForList(sql, String.class, tenantId, institutionId);
    }

    private InstitutionAppView mapApp(ResultSet rs, int rowNum) throws SQLException {
        return new InstitutionAppView(
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("app_key"),
                rs.getString("app_secret"),
                rs.getString("sign_type"),
                rs.getString("callback_url"),
                rs.getBoolean("enabled"),
                List.of()
        );
    }
}
