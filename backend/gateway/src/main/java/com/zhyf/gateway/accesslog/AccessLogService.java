package com.zhyf.gateway.accesslog;

import com.zhyf.gateway.app.InstitutionAppView;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AccessLogService {

    private static final Logger log = LoggerFactory.getLogger(AccessLogService.class);
    private final JdbcTemplate jdbcTemplate;

    public AccessLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String path, String requestIp, String appKey, InstitutionAppView app, String resultCode) {
        log.info(
                "gateway access path={} ip={} appKey={} tenantId={} institutionId={} result={}",
                path,
                requestIp,
                appKey,
                app == null ? null : app.tenantId(),
                app == null ? null : app.institutionId(),
                resultCode
        );
        try {
            insert(path, requestIp, appKey, app, resultCode);
        } catch (RuntimeException ex) {
            log.warn("gateway access log persist failed path={} ip={} appKey={} result={} error={}",
                    path, requestIp, appKey, resultCode, ex.getMessage());
            log.debug("gateway access log persist stacktrace", ex);
        }
    }

    private void insert(String path, String requestIp, String appKey, InstitutionAppView app, String resultCode) {
        String sql = """
                insert into api_access_log (
                    id, tenant_id, institution_id, app_key, request_path, request_ip, result_code
                ) values (?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                UUID.randomUUID(),
                app == null ? null : app.tenantId(),
                app == null ? null : app.institutionId(),
                appKey,
                path,
                requestIp,
                resultCode
        );
    }
}
