package com.zhyf.ops.infrastructure;

import com.zhyf.ops.application.OpsRecords;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class OpsQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public OpsQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OpsRecords.EventOutboxRecord> findEventOutbox(String status, String eventType, int limit) {
        QueryParts query = new QueryParts("""
                select id, tenant_id, event_id, event_type, aggregate_type, aggregate_id, status,
                       retry_count, next_retry_at, created_at, published_at
                from event_outbox
                where 1 = 1
                """);
        query.addTextFilter("status", status);
        query.addTextFilter("event_type", eventType);
        query.append(" order by created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapEventOutboxRecord, query.args());
    }

    public List<OpsRecords.MessageConsumeRecord> findMessageConsumeLogs(
            String status,
            String consumerGroup,
            String eventId,
            int limit
    ) {
        QueryParts query = new QueryParts("""
                select id, consumer_group, message_id, event_id, status, created_at
                from message_consume_log
                where 1 = 1
                """);
        query.addTextFilter("status", status);
        query.addTextFilter("consumer_group", consumerGroup);
        query.addTextFilter("event_id", eventId);
        query.append(" order by created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapMessageConsumeRecord, query.args());
    }

    public List<OpsRecords.OrderValidationRecord> findOrderValidationRecords(
            UUID orderId,
            String validationStatus,
            int limit
    ) {
        QueryParts query = new QueryParts("""
                select id, tenant_id, order_id, event_id, validation_status, validation_message, created_at
                from order_validation_record
                where 1 = 1
                """);
        if (orderId != null) {
            query.append(" and order_id = ?");
            query.add(orderId);
        }
        query.addTextFilter("validation_status", validationStatus);
        query.append(" order by created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapOrderValidationRecord, query.args());
    }

    public List<OpsRecords.ApiAccessLogRecord> findApiAccessLogs(String appKey, String resultCode, int limit) {
        QueryParts query = new QueryParts("""
                select id, tenant_id, institution_id, app_key, request_path, request_ip, result_code, created_at
                from api_access_log
                where 1 = 1
                """);
        query.addTextFilter("app_key", appKey);
        query.addTextFilter("result_code", resultCode);
        query.append(" order by created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapApiAccessLogRecord, query.args());
    }

    private OpsRecords.EventOutboxRecord mapEventOutboxRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OpsRecords.EventOutboxRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                instant(rs, "next_retry_at"),
                instant(rs, "created_at"),
                instant(rs, "published_at")
        );
    }

    private OpsRecords.MessageConsumeRecord mapMessageConsumeRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OpsRecords.MessageConsumeRecord(
                rs.getObject("id", UUID.class),
                rs.getString("consumer_group"),
                rs.getString("message_id"),
                rs.getString("event_id"),
                rs.getString("status"),
                instant(rs, "created_at")
        );
    }

    private OpsRecords.OrderValidationRecord mapOrderValidationRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OpsRecords.OrderValidationRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("event_id"),
                rs.getString("validation_status"),
                rs.getString("validation_message"),
                instant(rs, "created_at")
        );
    }

    private OpsRecords.ApiAccessLogRecord mapApiAccessLogRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OpsRecords.ApiAccessLogRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("app_key"),
                rs.getString("request_path"),
                rs.getString("request_ip"),
                rs.getString("result_code"),
                instant(rs, "created_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
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
