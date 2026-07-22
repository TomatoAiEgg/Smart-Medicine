package com.zhyf.ops.infrastructure;

import com.zhyf.ops.application.OpsRecords;
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
public class OpsQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public OpsQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<OpsRecords.OrderIdentityRecord> findOrderIdentity(String orderNo, String externalOrderNo) {
        QueryParts query = new QueryParts("""
                select id, tenant_id, institution_id, order_no, external_order_no, status, created_at, updated_at
                from order_main
                where 1 = 1
                """);
        query.addTextFilter("order_no", orderNo);
        query.addTextFilter("external_order_no", externalOrderNo);
        query.append(" order by created_at desc limit 1");
        return jdbcTemplate.query(query.sql(), this::mapOrderIdentityRecord, query.args()).stream().findFirst();
    }

    public List<OpsRecords.OrderStatusLogRecord> findOrderStatusLogs(UUID orderId, int limit) {
        String sql = """
                select id, tenant_id, order_id, from_status, to_status, operator_type,
                       operator_id, source, reason, created_at
                from order_status_log
                where order_id = ?
                order by created_at desc limit ?
                """;
        return jdbcTemplate.query(sql, this::mapOrderStatusLogRecord, orderId, limit);
    }

    public List<OpsRecords.WorkflowTaskRecord> findWorkflowTasks(UUID orderId, int limit) {
        String sql = """
                select id, tenant_id, order_id, task_type, task_status, source_event_id,
                       assigned_to, review_comment, created_at, updated_at, completed_at
                from workflow_task
                where order_id = ?
                order by created_at desc limit ?
                """;
        return jdbcTemplate.query(sql, this::mapWorkflowTaskRecord, orderId, limit);
    }

    public List<OpsRecords.EventOutboxRecord> findEventOutbox(String status, String eventType, int limit) {
        QueryParts query = new QueryParts("""
                select id, tenant_id, event_id, event_type, topic, tag, source,
                       aggregate_type, aggregate_id, status, retry_count, max_retry_count,
                       next_retry_at, last_error, created_at, updated_at, published_at
                from event_outbox
                where 1 = 1
                """);
        query.addTextFilter("status", status);
        query.addTextFilter("event_type", eventType);
        query.append(" order by created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapEventOutboxRecord, query.args());
    }

    public List<OpsRecords.EventOutboxRecord> findEventOutboxByAggregateId(String aggregateId, int limit) {
        QueryParts query = new QueryParts("""
                select id, tenant_id, event_id, event_type, topic, tag, source,
                       aggregate_type, aggregate_id, status, retry_count, max_retry_count,
                       next_retry_at, last_error, created_at, updated_at, published_at
                from event_outbox
                where aggregate_id = ?
                """);
        query.add(aggregateId);
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
                select id, consumer_group, message_id, event_id, topic, tag, aggregate_id,
                       status, retry_count, last_error, trace_endpoint,
                       consume_started_at, consume_finished_at, created_at, updated_at
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

    public List<OpsRecords.MessageConsumeRecord> findMessageConsumeLogsByAggregateId(String aggregateId, int limit) {
        QueryParts query = new QueryParts("""
                select id, consumer_group, message_id, event_id, topic, tag, aggregate_id,
                       status, retry_count, last_error, trace_endpoint,
                       consume_started_at, consume_finished_at, created_at, updated_at
                from message_consume_log
                where aggregate_id = ?
                """);
        query.add(aggregateId);
        query.append(" order by created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapMessageConsumeRecord, query.args());
    }

    public List<OpsRecords.DeadLetterRecord> findDeadLetters(
            String status,
            String topic,
            String eventId,
            int limit
    ) {
        QueryParts query = new QueryParts("""
                select id, event_id, topic, tag, consumer_group, aggregate_id,
                       error_message, retry_count, status, operator, remark,
                       created_at, updated_at
                from dead_letter_record
                where 1 = 1
                """);
        if (StringUtils.hasText(status)) {
            query.addTextFilter("status", status);
        } else {
            query.addTextFilter("status", "OPEN");
        }
        query.addTextFilter("topic", topic);
        query.addTextFilter("event_id", eventId);
        query.append(" order by updated_at desc, created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapDeadLetterRecord, query.args());
    }

    public List<OpsRecords.DeadLetterRecord> findDeadLettersByAggregateId(String aggregateId, int limit) {
        QueryParts query = new QueryParts("""
                select id, event_id, topic, tag, consumer_group, aggregate_id,
                       error_message, retry_count, status, operator, remark,
                       created_at, updated_at
                from dead_letter_record
                where aggregate_id = ?
                """);
        query.add(aggregateId);
        query.append(" order by updated_at desc, created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapDeadLetterRecord, query.args());
    }

    public List<OpsRecords.DeadLetterRecord> findDeadLetterById(UUID id) {
        String sql = """
                select id, event_id, topic, tag, consumer_group, aggregate_id,
                       error_message, retry_count, status, operator, remark,
                       created_at, updated_at
                from dead_letter_record
                where id = ?
                """;
        return jdbcTemplate.query(sql, this::mapDeadLetterRecord, id);
    }

    public int resetDeadLetterForReplay(UUID id) {
        String sql = """
                with target as (
                    select event_id, consumer_group
                    from dead_letter_record
                    where id = ? and status = 'OPEN'
                ),
                outbox_reset as (
                    update event_outbox o
                    set status = 'NEW',
                        retry_count = 0,
                        next_retry_at = now(),
                        last_error = null,
                        published_at = null,
                        updated_at = now()
                    from target t
                    where o.event_id = t.event_id
                    returning 1
                ),
                consume_reset as (
                    update message_consume_log m
                    set status = 'FAILED_RETRYABLE',
                        retry_count = 0,
                        last_error = null,
                        consume_started_at = null,
                        consume_finished_at = null,
                        updated_at = now()
                    from target t
                    where m.event_id = t.event_id
                      and (t.consumer_group is null or m.consumer_group = t.consumer_group)
                    returning 1
                )
                select count(*) from outbox_reset
                """;
        Long value = jdbcTemplate.queryForObject(sql, Long.class, id);
        return value == null ? 0 : value.intValue();
    }

    public int markDeadLetterReplayed(UUID id, String operator, String remark) {
        String sql = """
                update dead_letter_record
                set status = 'REPLAYED',
                    operator = ?,
                    remark = ?,
                    updated_at = now()
                where id = ? and status = 'OPEN'
                """;
        return jdbcTemplate.update(sql, operator, remark, id);
    }

    public int closeDeadLetter(UUID id, String operator, String remark) {
        String sql = """
                update dead_letter_record
                set status = 'CLOSED',
                    operator = ?,
                    remark = ?,
                    updated_at = now()
                where id = ? and status = 'OPEN'
                """;
        return jdbcTemplate.update(sql, operator, remark, id);
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

    public List<OpsRecords.ApiAccessLogRecord> findRecentApiAccessLogsByInstitution(UUID institutionId, int limit) {
        String sql = """
                select id, tenant_id, institution_id, app_key, request_path, request_ip, result_code, created_at
                from api_access_log
                where institution_id = ?
                   or app_key in (
                       select app_key
                       from institution_app
                       where institution_id = ?
                   )
                order by created_at desc limit ?
                """;
        return jdbcTemplate.query(sql, this::mapApiAccessLogRecord, institutionId, institutionId, limit);
    }

    public List<OpsRecords.CallbackRecord> findCallbackRecordsByOrderId(UUID orderId, int limit) {
        String sql = """
                select id, tenant_id, order_id, callback_type, business_id, request_url,
                       response_body, status, retry_count, next_retry_at, created_at, updated_at
                from callback_record
                where order_id = ?
                order by created_at desc limit ?
                """;
        return jdbcTemplate.query(sql, this::mapCallbackRecord, orderId, limit);
    }

    public List<OpsRecords.OperationLogRecord> findOperationLogsByOrderId(UUID orderId, int limit) {
        String sql = """
                select id, tenant_id, order_id, prescription_id, event_id, operator,
                       action, result, reason, created_at
                from operation_log
                where order_id = ?
                order by created_at desc limit ?
                """;
        return jdbcTemplate.query(sql, this::mapOperationLogRecord, orderId, limit);
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

    public List<OpsRecords.IntegrationRetryIssueRecord> findIntegrationRetryIssues(
            String taskStatus,
            String taskType,
            String businessKey,
            String sourceSystem,
            int limit
    ) {
        QueryParts query = new QueryParts("""
                select
                    t.id as task_id,
                    t.message_id,
                    t.task_type,
                    t.target_system,
                    t.business_key,
                    t.request_url,
                    t.response_body,
                    t.task_status,
                    t.retry_count,
                    t.next_retry_at,
                    t.created_at as task_created_at,
                    t.updated_at as task_updated_at,
                    t.processed_at,
                    m.source_type,
                    m.source_system,
                    m.external_message_id,
                    m.message_type,
                    m.process_status,
                    m.failure_reason
                from integration_retry_task t
                join integration_message m on m.id = t.message_id
                where 1 = 1
                """);
        if (StringUtils.hasText(taskStatus)) {
            query.addTextFilter("t.task_status", taskStatus);
        } else {
            query.append(" and t.task_status in ('FAILED', 'DEAD')");
        }
        query.addTextFilter("t.task_type", taskType);
        query.addTextFilter("t.business_key", businessKey);
        query.addTextFilter("m.source_system", sourceSystem);
        query.append(" order by t.updated_at desc, t.created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapIntegrationRetryIssueRecord, query.args());
    }

    public List<OpsRecords.IntegrationRetryIssueRecord> findIntegrationRetriesByBusinessKeys(
            List<String> businessKeys,
            int limit
    ) {
        if (businessKeys == null || businessKeys.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", businessKeys.stream().map(ignored -> "?").toList());
        QueryParts query = new QueryParts("""
                select
                    t.id as task_id,
                    t.message_id,
                    t.task_type,
                    t.target_system,
                    t.business_key,
                    t.request_url,
                    t.response_body,
                    t.task_status,
                    t.retry_count,
                    t.next_retry_at,
                    t.created_at as task_created_at,
                    t.updated_at as task_updated_at,
                    t.processed_at,
                    m.source_type,
                    m.source_system,
                    m.external_message_id,
                    m.message_type,
                    m.process_status,
                    m.failure_reason
                from integration_retry_task t
                join integration_message m on m.id = t.message_id
                where t.business_key in (
                """);
        query.append(placeholders);
        query.append(") order by t.updated_at desc, t.created_at desc limit ?");
        businessKeys.forEach(query::add);
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapIntegrationRetryIssueRecord, query.args());
    }

    public OpsRecords.OpsHealthOverview loadHealthOverview(int recentHours) {
        return new OpsRecords.OpsHealthOverview(
                recentHours,
                countByStatus("event_outbox", "status", "NEW"),
                countByStatus("event_outbox", "status", "PUBLISH_FAILED"),
                countByStatuses("message_consume_log", "status", List.of("FAILED_RETRYABLE", "FAILED_FATAL", "DEAD")),
                countByStatus("order_validation_record", "validation_status", "REJECTED"),
                countByStatus("callback_record", "status", "FAILED"),
                countByStatus("callback_record", "status", "DEAD"),
                countByStatus("integration_retry_task", "task_status", "FAILED"),
                countByStatus("integration_retry_task", "task_status", "DEAD"),
                countRecentAccess(recentHours)
        );
    }

    private long countByStatus(String table, String statusColumn, String status) {
        String sql = "select count(*) from " + table + " where " + statusColumn + " = ?";
        Long value = jdbcTemplate.queryForObject(sql, Long.class, status);
        return value == null ? 0 : value;
    }

    private long countByStatuses(String table, String statusColumn, List<String> statuses) {
        String placeholders = String.join(",", statuses.stream().map(ignored -> "?").toList());
        String sql = "select count(*) from " + table + " where " + statusColumn + " in (" + placeholders + ")";
        Long value = jdbcTemplate.queryForObject(sql, Long.class, statuses.toArray());
        return value == null ? 0 : value;
    }

    private long countRecentAccess(int recentHours) {
        String sql = """
                select count(*)
                from api_access_log
                where created_at >= now() - (? * interval '1 hour')
                """;
        Long value = jdbcTemplate.queryForObject(sql, Long.class, recentHours);
        return value == null ? 0 : value;
    }

    private OpsRecords.EventOutboxRecord mapEventOutboxRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OpsRecords.EventOutboxRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getString("topic"),
                rs.getString("tag"),
                rs.getString("source"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getInt("max_retry_count"),
                instant(rs, "next_retry_at"),
                rs.getString("last_error"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "published_at")
        );
    }

    private OpsRecords.OrderIdentityRecord mapOrderIdentityRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OpsRecords.OrderIdentityRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("status"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private OpsRecords.OrderStatusLogRecord mapOrderStatusLogRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OpsRecords.OrderStatusLogRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("operator_type"),
                rs.getString("operator_id"),
                rs.getString("source"),
                rs.getString("reason"),
                instant(rs, "created_at")
        );
    }

    private OpsRecords.WorkflowTaskRecord mapWorkflowTaskRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OpsRecords.WorkflowTaskRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("task_type"),
                rs.getString("task_status"),
                rs.getString("source_event_id"),
                rs.getString("assigned_to"),
                rs.getString("review_comment"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "completed_at")
        );
    }

    private OpsRecords.MessageConsumeRecord mapMessageConsumeRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OpsRecords.MessageConsumeRecord(
                rs.getObject("id", UUID.class),
                rs.getString("consumer_group"),
                rs.getString("message_id"),
                rs.getString("event_id"),
                rs.getString("topic"),
                rs.getString("tag"),
                rs.getString("aggregate_id"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getString("last_error"),
                rs.getString("trace_endpoint"),
                instant(rs, "consume_started_at"),
                instant(rs, "consume_finished_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private OpsRecords.CallbackRecord mapCallbackRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OpsRecords.CallbackRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("callback_type"),
                rs.getString("business_id"),
                rs.getString("request_url"),
                rs.getString("response_body"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                instant(rs, "next_retry_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private OpsRecords.OperationLogRecord mapOperationLogRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OpsRecords.OperationLogRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getObject("prescription_id", UUID.class),
                rs.getString("event_id"),
                rs.getString("operator"),
                rs.getString("action"),
                rs.getString("result"),
                rs.getString("reason"),
                instant(rs, "created_at")
        );
    }

    private OpsRecords.DeadLetterRecord mapDeadLetterRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OpsRecords.DeadLetterRecord(
                rs.getObject("id", UUID.class),
                rs.getString("event_id"),
                rs.getString("topic"),
                rs.getString("tag"),
                rs.getString("consumer_group"),
                rs.getString("aggregate_id"),
                rs.getString("error_message"),
                rs.getInt("retry_count"),
                rs.getString("status"),
                rs.getString("operator"),
                rs.getString("remark"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
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

    private OpsRecords.IntegrationRetryIssueRecord mapIntegrationRetryIssueRecord(ResultSet rs, int rowNum)
            throws SQLException {
        return new OpsRecords.IntegrationRetryIssueRecord(
                rs.getObject("task_id", UUID.class),
                rs.getObject("message_id", UUID.class),
                rs.getString("task_type"),
                rs.getString("target_system"),
                rs.getString("business_key"),
                rs.getString("request_url"),
                rs.getString("response_body"),
                rs.getString("task_status"),
                rs.getInt("retry_count"),
                instant(rs, "next_retry_at"),
                instant(rs, "task_created_at"),
                instant(rs, "task_updated_at"),
                instant(rs, "processed_at"),
                rs.getString("source_type"),
                rs.getString("source_system"),
                rs.getString("external_message_id"),
                rs.getString("message_type"),
                rs.getString("process_status"),
                rs.getString("failure_reason")
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
