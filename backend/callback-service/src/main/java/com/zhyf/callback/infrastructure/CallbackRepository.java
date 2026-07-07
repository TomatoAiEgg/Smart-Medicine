package com.zhyf.callback.infrastructure;

import com.zhyf.callback.application.CallbackRecords;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class CallbackRepository {

    private final JdbcTemplate jdbcTemplate;

    public CallbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<OrderCallbackTarget> findOrderTarget(UUID orderId) {
        String sql = """
                select id as order_id, tenant_id, order_no, callback_url, status
                from order_main
                where id = ?
                """;
        return jdbcTemplate.query(sql, this::mapOrderTarget, orderId).stream().findFirst();
    }

    public Optional<CallbackRecords.CallbackRecord> findByBusinessKey(String callbackType, String businessId) {
        String sql = baseQuery() + """
                where c.callback_type = ?
                  and c.business_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapCallbackRecord, callbackType, businessId).stream().findFirst();
    }

    public Optional<CallbackRecords.CallbackRecord> findById(UUID id) {
        String sql = baseQuery() + """
                where c.id = ?
                """;
        return jdbcTemplate.query(sql, this::mapCallbackRecord, id).stream().findFirst();
    }

    public List<CallbackRecords.CallbackRecord> findRecords(String status, String callbackType, UUID orderId, int limit) {
        QueryParts query = new QueryParts(baseQuery() + " where 1 = 1");
        query.addTextFilter("c.status", status);
        query.addTextFilter("c.callback_type", callbackType);
        if (orderId != null) {
            query.append(" and c.order_id = ?");
            query.add(orderId);
        }
        query.append(" order by c.created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapCallbackRecord, query.args());
    }

    public int createRecord(
            UUID id,
            UUID tenantId,
            UUID orderId,
            String callbackType,
            String businessId,
            String requestUrl,
            String requestBody,
            String status
    ) {
        String sql = """
                insert into callback_record (
                    id, tenant_id, order_id, callback_type, business_id,
                    request_url, request_headers, request_body, status
                ) values (?, ?, ?, ?, ?, ?, '{}'::jsonb, ?::jsonb, ?)
                """;
        return jdbcTemplate.update(sql, id, tenantId, orderId, callbackType, businessId, requestUrl, requestBody, status);
    }

    public int markSucceeded(UUID id, String responseBody) {
        String sql = """
                update callback_record
                set status = 'SUCCESS',
                    response_body = ?,
                    next_retry_at = null,
                    updated_at = now()
                where id = ?
                """;
        return jdbcTemplate.update(sql, responseBody, id);
    }

    public int markFailed(UUID id, String responseBody, Instant nextRetryAt) {
        String sql = """
                update callback_record
                set status = 'FAILED',
                    response_body = ?,
                    retry_count = retry_count + 1,
                    next_retry_at = ?,
                    updated_at = now()
                where id = ?
                """;
        return jdbcTemplate.update(sql, responseBody, nextRetryAt, id);
    }

    public int replay(UUID id, Instant nextRetryAt) {
        String sql = """
                update callback_record
                set status = 'PENDING',
                    retry_count = retry_count + 1,
                    next_retry_at = ?,
                    updated_at = now()
                where id = ?
                """;
        return jdbcTemplate.update(sql, nextRetryAt, id);
    }

    private String baseQuery() {
        return """
                select
                    c.id,
                    c.tenant_id,
                    c.order_id,
                    o.order_no,
                    c.callback_type,
                    c.business_id,
                    c.request_url,
                    c.request_body::text as request_body,
                    c.response_body,
                    c.status,
                    c.retry_count,
                    c.next_retry_at,
                    c.created_at,
                    c.updated_at
                from callback_record c
                left join order_main o on o.id = c.order_id
                """;
    }

    private OrderCallbackTarget mapOrderTarget(ResultSet rs, int rowNum) throws SQLException {
        return new OrderCallbackTarget(
                rs.getObject("order_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("callback_url"),
                rs.getString("status")
        );
    }

    private CallbackRecords.CallbackRecord mapCallbackRecord(ResultSet rs, int rowNum) throws SQLException {
        return new CallbackRecords.CallbackRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("callback_type"),
                rs.getString("business_id"),
                rs.getString("request_url"),
                rs.getString("request_body"),
                rs.getString("response_body"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                instant(rs, "next_retry_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public record OrderCallbackTarget(
            UUID orderId,
            UUID tenantId,
            String orderNo,
            String callbackUrl,
            String orderStatus
    ) {
    }

    private static final class QueryParts {
        private final StringBuilder sql;
        private final List<Object> args = new ArrayList<>();

        private QueryParts(String baseSql) {
            this.sql = new StringBuilder(baseSql);
        }

        private void addTextFilter(String column, String value) {
            if (StringUtils.hasText(value)) {
                append(" and " + column + " = ?");
                add(value);
            }
        }

        private void append(String value) {
            sql.append(value);
        }

        private void add(Object value) {
            args.add(value);
        }

        private String sql() {
            return sql.toString();
        }

        private Object[] args() {
            return args.toArray();
        }
    }
}
