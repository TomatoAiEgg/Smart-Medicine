package com.zhyf.message.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.message.application.SmsSendRecords;
import com.zhyf.message.application.SmsTemplateRecords;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class SmsSendRecordRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SmsSendRecordRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public SmsSendRecords.SmsSendResult insertSingleSendRecord(
            UUID id,
            SmsTemplateRecords.SmsTemplateRecord template,
            String receiverPhone,
            String receiverName,
            String relatedOrderNo,
            String content,
            Map<String, String> variables,
            String sendStatus,
            String operator
    ) {
        String sql = """
                insert into sms_send_record (
                    id, tenant_id, template_id, template_code, template_name,
                    receiver_phone, receiver_name, related_order_no, signature, content,
                    variables, send_status, operator
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                returning id, tenant_id, template_id, template_code, template_name,
                          receiver_phone, receiver_name, related_order_no, signature, content,
                          send_status, provider_message_id, failure_reason, retry_count,
                          operator, created_at, updated_at, sent_at
                """;
        return jdbcTemplate.queryForObject(
                sql,
                this::mapSmsSendResult,
                id,
                template.tenantId(),
                template.id(),
                template.templateCode(),
                template.templateName(),
                receiverPhone,
                receiverName,
                relatedOrderNo,
                template.signature(),
                content,
                variablesJson(variables),
                sendStatus,
                operator
        );
    }

    public SmsSendRecords.SmsRecordPage searchSmsRecords(SmsSendRecords.SmsRecordQuery query) {
        QueryParts filters = smsRecordFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from sms_send_record r
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select id, tenant_id, template_id, template_code, template_name,
                       receiver_phone, receiver_name, related_order_no, signature, content,
                       send_status, provider_message_id, failure_reason, retry_count,
                       operator, created_at, updated_at, sent_at
                from sms_send_record r
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by created_at desc, id desc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new SmsSendRecords.SmsRecordPage(
                jdbcTemplate.query(listQuery.sql(), this::mapSmsSendResult, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    private String variablesJson(Map<String, String> variables) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("SMS_VARIABLES_INVALID", "Sms variables are invalid");
        }
    }

    private QueryParts smsRecordFilters(SmsSendRecords.SmsRecordQuery query) {
        QueryParts filters = new QueryParts("");
        if (StringUtils.hasText(query.keyword())) {
            filters.append("""
                     and (
                        r.template_code ilike ?
                        or r.template_name ilike ?
                        or r.receiver_phone ilike ?
                        or coalesce(r.receiver_name, '') ilike ?
                        or coalesce(r.related_order_no, '') ilike ?
                        or r.content ilike ?
                    )
                    """);
            String pattern = "%" + query.keyword().trim() + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (StringUtils.hasText(query.sendStatus())) {
            filters.append(" and r.send_status = ?");
            filters.add(query.sendStatus().trim());
        }
        return filters;
    }

    private SmsSendRecords.SmsSendResult mapSmsSendResult(ResultSet rs, int rowNum)
            throws SQLException {
        return new SmsSendRecords.SmsSendResult(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("template_id", UUID.class),
                rs.getString("template_code"),
                rs.getString("template_name"),
                rs.getString("receiver_phone"),
                rs.getString("receiver_name"),
                rs.getString("related_order_no"),
                rs.getString("signature"),
                rs.getString("content"),
                rs.getString("send_status"),
                rs.getString("provider_message_id"),
                rs.getString("failure_reason"),
                rs.getInt("retry_count"),
                rs.getString("operator"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "sent_at")
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

        private void append(String value) {
            sql.append(value);
        }

        private void add(Object value) {
            args.add(value);
        }

        private void addAll(List<Object> values) {
            args.addAll(values);
        }

        private List<Object> argsList() {
            return args;
        }

        private String sql() {
            return sql.toString();
        }

        private Object[] args() {
            return args.toArray();
        }
    }
}
