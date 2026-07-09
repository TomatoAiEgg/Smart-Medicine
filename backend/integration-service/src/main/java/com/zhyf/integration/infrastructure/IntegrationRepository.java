package com.zhyf.integration.infrastructure;

import com.zhyf.integration.application.IntegrationRecords;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class IntegrationRepository {

    private final JdbcTemplate jdbcTemplate;

    public IntegrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<IntegrationRecords.IntegrationMessageRecord> findMessage(
            String sourceType,
            String sourceSystem,
            String externalMessageId
    ) {
        String sql = """
                select id, source_type, source_system, external_message_id, message_type, business_key,
                       process_status, normalized_payload::text as normalized_payload, raw_payload, failure_reason,
                       created_at, updated_at, processed_at
                from integration_message
                where source_type = ? and source_system = ? and external_message_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapMessage, sourceType, sourceSystem, externalMessageId)
                .stream()
                .findFirst();
    }

    public IntegrationRecords.IntegrationMessageRecord createMessage(
            UUID messageId,
            String sourceType,
            String sourceSystem,
            String externalMessageId,
            String messageType,
            String businessKey,
            String normalizedPayload,
            String rawPayload
    ) {
        Instant now = Instant.now();
        String sql = """
                insert into integration_message (
                    id, source_type, source_system, external_message_id, message_type, business_key,
                    process_status, normalized_payload, raw_payload, failure_reason, created_at, updated_at, processed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                messageId,
                sourceType,
                sourceSystem,
                externalMessageId,
                messageType,
                businessKey,
                "PENDING",
                StringUtils.hasText(normalizedPayload) ? normalizedPayload : "{}",
                rawPayload == null ? "" : rawPayload,
                null,
                offsetDateTime(now),
                offsetDateTime(now),
                null
        );
        return findMessage(sourceType, sourceSystem, externalMessageId)
                .orElse(new IntegrationRecords.IntegrationMessageRecord(
                        messageId,
                        sourceType,
                        sourceSystem,
                        externalMessageId,
                        messageType,
                        businessKey,
                        "PENDING",
                        StringUtils.hasText(normalizedPayload) ? normalizedPayload : "{}",
                        rawPayload == null ? "" : rawPayload,
                        null,
                        now,
                        now,
                        null
                ));
    }

    public List<IntegrationRecords.IntegrationMessageRecord> findMessages(
            String sourceType,
            String processStatus,
            String businessKey,
            int limit
    ) {
        QueryParts query = new QueryParts("""
                select id, source_type, source_system, external_message_id, message_type, business_key,
                       process_status, normalized_payload::text as normalized_payload, raw_payload, failure_reason,
                       created_at, updated_at, processed_at
                from integration_message
                where 1 = 1
                """);
        query.addTextFilter("source_type", sourceType);
        query.addTextFilter("process_status", processStatus);
        query.addTextFilter("business_key", businessKey);
        query.append(" order by created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapMessage, query.args());
    }

    public Optional<IntegrationRecords.IntegrationRetryTaskRecord> findRetryTask(UUID messageId, String taskType) {
        String sql = retryTaskBaseQuery() + """
                where message_id = ? and task_type = ?
                """;
        return jdbcTemplate.query(sql, this::mapRetryTask, messageId, taskType).stream().findFirst();
    }

    public IntegrationRecords.IntegrationRetryTaskRecord createRetryTask(
            UUID taskId,
            UUID messageId,
            String taskType,
            String targetSystem,
            String businessKey,
            String requestUrl,
            String requestBody,
            Instant nextRetryAt
    ) {
        Instant now = Instant.now();
        String sql = """
                insert into integration_retry_task (
                    id, message_id, task_type, target_system, business_key, request_url,
                    request_headers, request_body, response_body, task_status, retry_count,
                    next_retry_at, created_at, updated_at, processed_at
                ) values (?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                taskId,
                messageId,
                taskType,
                targetSystem,
                businessKey,
                requestUrl,
                requestBody == null ? "" : requestBody,
                null,
                "PENDING",
                0,
                offsetDateTime(nextRetryAt),
                offsetDateTime(now),
                offsetDateTime(now),
                null
        );
        return findRetryTask(messageId, taskType)
                .orElse(new IntegrationRecords.IntegrationRetryTaskRecord(
                        taskId,
                        messageId,
                        taskType,
                        targetSystem,
                        businessKey,
                        requestUrl,
                        requestBody == null ? "" : requestBody,
                        null,
                        "PENDING",
                        0,
                        nextRetryAt,
                        now,
                        now,
                        null
                ));
    }

    public List<IntegrationRecords.IntegrationRetryTaskRecord> findRetryTasks(
            String taskType,
            String taskStatus,
            String businessKey,
            int limit
    ) {
        QueryParts query = new QueryParts(retryTaskBaseQuery() + " where 1 = 1");
        query.addTextFilter("task_type", taskType);
        query.addTextFilter("task_status", taskStatus);
        query.addTextFilter("business_key", businessKey);
        query.append(" order by created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapRetryTask, query.args());
    }

    public List<IntegrationRecords.IntegrationRetryTaskRecord> findDueRetryTasks(Instant now, int limit) {
        String sql = retryTaskBaseQuery() + """
                where task_status in ('PENDING', 'FAILED')
                  and (next_retry_at is null or next_retry_at <= ?)
                order by next_retry_at nulls first, created_at asc
                limit ?
                """;
        return jdbcTemplate.query(sql, this::mapRetryTask, offsetDateTime(now), limit);
    }

    public int markRetryTaskSucceeded(UUID taskId, String responseBody) {
        String sql = """
                update integration_retry_task
                set task_status = 'SUCCESS',
                    response_body = ?,
                    next_retry_at = null,
                    processed_at = now(),
                    updated_at = now()
                where id = ?
                """;
        return jdbcTemplate.update(sql, responseBody, taskId);
    }

    public int markRetryTaskFailed(UUID taskId, String responseBody, Instant nextRetryAt) {
        String sql = """
                update integration_retry_task
                set task_status = 'FAILED',
                    response_body = ?,
                    retry_count = retry_count + 1,
                    next_retry_at = ?,
                    updated_at = now()
                where id = ?
                """;
        return jdbcTemplate.update(sql, responseBody, offsetDateTime(nextRetryAt), taskId);
    }

    public int markRetryTaskDead(UUID taskId, String responseBody) {
        String sql = """
                update integration_retry_task
                set task_status = 'DEAD',
                    response_body = ?,
                    retry_count = retry_count + 1,
                    next_retry_at = null,
                    processed_at = now(),
                    updated_at = now()
                where id = ?
                """;
        return jdbcTemplate.update(sql, responseBody, taskId);
    }

    public int markMessageStatus(UUID messageId, String processStatus, String failureReason) {
        String sql = """
                update integration_message
                set process_status = ?,
                    failure_reason = ?,
                    processed_at = case when ? in ('SUCCESS', 'DEAD') then now() else processed_at end,
                    updated_at = now()
                where id = ?
                """;
        return jdbcTemplate.update(sql, processStatus, failureReason, processStatus, messageId);
    }

    public Optional<IntegrationRecords.HospitalOrderRecord> findHospitalOrderByPrescription(
            String prescriptionNo,
            String phone
    ) {
        String sql = """
                select o.tenant_id, o.id as order_id, i.institution_name, o.order_no, o.external_order_no,
                       o.status as order_status, p.prescription_no, p.status as prescription_status,
                       o.patient_name, o.receiver_name, o.receiver_phone,
                       concat_ws('', o.receiver_province, o.receiver_city, o.receiver_zone, o.receiver_address)
                           as receiver_address,
                       s.logistics_no, s.logistics_company, s.logistics_status, o.created_at
                from prescription p
                join order_main o on o.id = p.order_id
                join institution i on i.id = o.institution_id
                left join lateral (
                    select logistics_no, logistics_company, logistics_status
                    from shipment
                    where order_id = o.id
                    order by created_at desc
                    limit 1
                ) s on true
                where p.prescription_no = ?
                  and (o.patient_phone = ? or o.receiver_phone = ?)
                order by o.created_at desc
                limit 1
                """;
        return jdbcTemplate.query(sql, this::mapHospitalOrder, prescriptionNo, phone, phone).stream().findFirst();
    }

    private IntegrationRecords.IntegrationMessageRecord mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return new IntegrationRecords.IntegrationMessageRecord(
                rs.getObject("id", UUID.class),
                rs.getString("source_type"),
                rs.getString("source_system"),
                rs.getString("external_message_id"),
                rs.getString("message_type"),
                rs.getString("business_key"),
                rs.getString("process_status"),
                rs.getString("normalized_payload"),
                rs.getString("raw_payload"),
                rs.getString("failure_reason"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "processed_at")
        );
    }

    private IntegrationRecords.HospitalOrderRecord mapHospitalOrder(ResultSet rs, int rowNum) throws SQLException {
        return new IntegrationRecords.HospitalOrderRecord(
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("institution_name"),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("order_status"),
                rs.getString("prescription_no"),
                rs.getString("prescription_status"),
                rs.getString("patient_name"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("receiver_address"),
                rs.getString("logistics_no"),
                rs.getString("logistics_company"),
                rs.getString("logistics_status"),
                instant(rs, "created_at")
        );
    }

    private IntegrationRecords.IntegrationRetryTaskRecord mapRetryTask(ResultSet rs, int rowNum) throws SQLException {
        return new IntegrationRecords.IntegrationRetryTaskRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("message_id", UUID.class),
                rs.getString("task_type"),
                rs.getString("target_system"),
                rs.getString("business_key"),
                rs.getString("request_url"),
                rs.getString("request_body"),
                rs.getString("response_body"),
                rs.getString("task_status"),
                rs.getInt("retry_count"),
                instant(rs, "next_retry_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "processed_at")
        );
    }

    private String retryTaskBaseQuery() {
        return """
                select id, message_id, task_type, target_system, business_key,
                       request_url, request_body, response_body, task_status, retry_count,
                       next_retry_at, created_at, updated_at, processed_at
                from integration_retry_task
                """;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime offsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
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
