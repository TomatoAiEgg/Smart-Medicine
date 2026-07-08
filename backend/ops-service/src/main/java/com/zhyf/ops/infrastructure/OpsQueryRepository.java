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

    public List<OpsRecords.LogisticsCallbackIssueRecord> findLogisticsCallbackIssues(
            String callbackStatus,
            String callbackType,
            String businessId,
            String orderNo,
            int limit
    ) {
        QueryParts query = new QueryParts("""
                select
                    c.id as callback_id,
                    c.tenant_id,
                    c.order_id,
                    coalesce(o.order_no, s.order_no) as order_no,
                    c.callback_type,
                    c.business_id,
                    c.request_url,
                    c.response_body,
                    c.status as callback_status,
                    c.retry_count,
                    c.next_retry_at,
                    c.created_at as callback_created_at,
                    c.updated_at as callback_updated_at,
                    s.id as shipment_id,
                    s.logistics_no,
                    s.logistics_company,
                    s.logistics_status,
                    latest_trace.trace_status as latest_trace_status,
                    latest_trace.trace_content as latest_trace_content,
                    latest_trace.trace_time as latest_trace_time
                from callback_record c
                left join shipment s on (
                    c.business_id = s.id::text
                    or c.business_id like s.id::text || ':%'
                    or c.business_id = s.logistics_no
                )
                left join order_main o on o.id = c.order_id
                left join lateral (
                    select trace_status, trace_content, trace_time
                    from shipment_trace st
                    where st.shipment_id = s.id
                    order by st.created_at desc
                    limit 1
                ) latest_trace on true
                where 1 = 1
                """);
        if (StringUtils.hasText(callbackStatus)) {
            query.addTextFilter("c.status", callbackStatus);
        } else {
            query.append(" and c.status in ('FAILED', 'DEAD')");
        }
        query.addTextFilter("c.callback_type", callbackType);
        if (StringUtils.hasText(businessId)) {
            query.append(" and (c.business_id = ? or s.logistics_no = ?)");
            query.add(businessId);
            query.add(businessId);
        }
        query.addTextFilter("coalesce(o.order_no, s.order_no)", orderNo);
        query.append(" order by c.updated_at desc, c.created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapLogisticsCallbackIssueRecord, query.args());
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

    private OpsRecords.LogisticsCallbackIssueRecord mapLogisticsCallbackIssueRecord(ResultSet rs, int rowNum)
            throws SQLException {
        return new OpsRecords.LogisticsCallbackIssueRecord(
                rs.getObject("callback_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("callback_type"),
                rs.getString("business_id"),
                rs.getString("request_url"),
                rs.getString("response_body"),
                rs.getString("callback_status"),
                rs.getInt("retry_count"),
                instant(rs, "next_retry_at"),
                instant(rs, "callback_created_at"),
                instant(rs, "callback_updated_at"),
                rs.getObject("shipment_id", UUID.class),
                rs.getString("logistics_no"),
                rs.getString("logistics_company"),
                rs.getString("logistics_status"),
                rs.getString("latest_trace_status"),
                rs.getString("latest_trace_content"),
                instant(rs, "latest_trace_time")
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
