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
