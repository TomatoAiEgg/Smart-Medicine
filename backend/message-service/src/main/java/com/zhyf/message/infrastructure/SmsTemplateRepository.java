package com.zhyf.message.infrastructure;

import com.zhyf.message.application.SmsTemplateRecords;
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
public class SmsTemplateRepository {

    private final JdbcTemplate jdbcTemplate;

    public SmsTemplateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SmsTemplateRecords.SmsTemplatePage searchSmsTemplates(SmsTemplateRecords.SmsTemplateQuery query) {
        QueryParts filters = smsTemplateFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from sms_template t
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select id, tenant_id, template_code, template_name, template_type, content_template,
                       signature, enabled, created_at, updated_at
                from sms_template t
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by enabled desc, updated_at desc, template_code asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new SmsTemplateRecords.SmsTemplatePage(
                jdbcTemplate.query(listQuery.sql(), this::mapSmsTemplateRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<SmsTemplateRecords.SmsTemplateRecord> findById(UUID id) {
        String sql = """
                select id, tenant_id, template_code, template_name, template_type, content_template,
                       signature, enabled, created_at, updated_at
                from sms_template
                where id = ?
                """;
        return jdbcTemplate.query(sql, this::mapSmsTemplateRecord, id).stream().findFirst();
    }

    public Optional<SmsTemplateRecords.SmsTemplateRecord> findByCode(UUID tenantId, String templateCode) {
        String sql = """
                select id, tenant_id, template_code, template_name, template_type, content_template,
                       signature, enabled, created_at, updated_at
                from sms_template
                where tenant_id = ? and template_code = ?
                """;
        return jdbcTemplate.query(sql, this::mapSmsTemplateRecord, tenantId, templateCode).stream().findFirst();
    }

    public SmsTemplateRecords.SmsTemplateRecord insertSmsTemplate(
            UUID id,
            UUID tenantId,
            String templateCode,
            String templateName,
            String templateType,
            String contentTemplate,
            String signature,
            boolean enabled
    ) {
        String sql = """
                insert into sms_template (
                    id, tenant_id, template_code, template_name, template_type,
                    content_template, signature, enabled
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                id,
                tenantId,
                templateCode,
                templateName,
                templateType,
                contentTemplate,
                signature,
                enabled
        );
        return findById(id).orElseThrow();
    }

    public SmsTemplateRecords.SmsTemplateRecord updateSmsTemplate(
            UUID id,
            String templateName,
            String templateType,
            String contentTemplate,
            String signature,
            boolean enabled
    ) {
        String sql = """
                update sms_template
                set template_name = ?,
                    template_type = ?,
                    content_template = ?,
                    signature = ?,
                    enabled = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, templateName, templateType, contentTemplate, signature, enabled, id);
        return findById(id).orElseThrow();
    }

    private QueryParts smsTemplateFilters(SmsTemplateRecords.SmsTemplateQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        t.template_code ilike ?
                        or t.template_name ilike ?
                        or t.content_template ilike ?
                        or coalesce(t.signature, '') ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        filters.addTextFilter("t.template_type", query.templateType());
        if (query.enabled() != null) {
            filters.append(" and t.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private SmsTemplateRecords.SmsTemplateRecord mapSmsTemplateRecord(ResultSet rs, int rowNum)
            throws SQLException {
        return new SmsTemplateRecords.SmsTemplateRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("template_code"),
                rs.getString("template_name"),
                rs.getString("template_type"),
                rs.getString("content_template"),
                rs.getString("signature"),
                rs.getBoolean("enabled"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
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
