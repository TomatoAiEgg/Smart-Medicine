package com.zhyf.order.infrastructure;

import com.zhyf.order.application.AdminExportTaskFile;
import com.zhyf.order.application.AdminExportTaskPage;
import com.zhyf.order.application.AdminExportTaskQuery;
import com.zhyf.order.application.AdminExportTaskRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class AdminExportTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminExportTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertTask(AdminExportTaskRecord task) {
        String sql = """
                insert into export_task (
                    id, tenant_id, task_type, task_name, task_status, query_param,
                    file_name, content_type, row_count, file_size_bytes,
                    failure_reason, requested_by, retry_count
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql,
                task.taskId(),
                task.tenantId(),
                task.taskType(),
                task.taskName(),
                task.taskStatus(),
                task.queryParam(),
                task.fileName(),
                task.contentType(),
                task.rowCount(),
                task.fileSizeBytes(),
                task.failureReason(),
                task.requestedBy(),
                task.retryCount());
    }

    public AdminExportTaskPage searchTasks(UUID tenantId, AdminExportTaskQuery query) {
        QueryParts filters = taskFilters(tenantId, query);
        QueryParts countQuery = new QueryParts("select count(*) from export_task where 1 = 1");
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        QueryParts listQuery = new QueryParts("""
                select id, tenant_id, task_type, task_name, task_status, query_param,
                       file_name, content_type, row_count, file_size_bytes, failure_reason,
                       requested_by, retry_count, started_at, completed_at, created_at, updated_at
                from export_task
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by created_at desc limit ? offset ?");
        listQuery.add(pageSize);
        listQuery.add((page - 1) * pageSize);

        return new AdminExportTaskPage(
                jdbcTemplate.query(listQuery.sql(), this::mapTask, listQuery.args()),
                total,
                page,
                pageSize
        );
    }

    public Optional<AdminExportTaskRecord> findTask(UUID tenantId, UUID taskId) {
        String sql = """
                select id, tenant_id, task_type, task_name, task_status, query_param,
                       file_name, content_type, row_count, file_size_bytes, failure_reason,
                       requested_by, retry_count, started_at, completed_at, created_at, updated_at
                from export_task
                where tenant_id = ? and id = ?
                """;
        return jdbcTemplate.query(sql, this::mapTask, tenantId, taskId).stream().findFirst();
    }

    public List<UUID> findPendingTaskIds(UUID tenantId, int limit) {
        String sql = """
                select id
                from export_task
                where tenant_id = ? and task_status = 'PENDING'
                order by created_at asc
                limit ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getObject("id", UUID.class), tenantId, Math.max(1, limit));
    }

    public void markRunning(UUID tenantId, UUID taskId, boolean incrementRetry) {
        String sql = """
                update export_task
                set task_status = 'RUNNING',
                    failure_reason = null,
                    started_at = now(),
                    completed_at = null,
                    retry_count = retry_count + ?,
                    updated_at = now()
                where tenant_id = ? and id = ?
                """;
        jdbcTemplate.update(sql, incrementRetry ? 1 : 0, tenantId, taskId);
    }

    public void markSuccess(
            UUID tenantId,
            UUID taskId,
            String fileName,
            String contentType,
            byte[] fileContent,
            int rowCount
    ) {
        String sql = """
                update export_task
                set task_status = 'SUCCESS',
                    file_name = ?,
                    content_type = ?,
                    file_content = ?,
                    row_count = ?,
                    file_size_bytes = ?,
                    failure_reason = null,
                    completed_at = now(),
                    updated_at = now()
                where tenant_id = ? and id = ?
                """;
        jdbcTemplate.update(sql, fileName, contentType, fileContent, rowCount, fileContent.length, tenantId, taskId);
    }

    public void markFailed(UUID tenantId, UUID taskId, String failureReason) {
        String sql = """
                update export_task
                set task_status = 'FAILED',
                    failure_reason = ?,
                    completed_at = now(),
                    updated_at = now()
                where tenant_id = ? and id = ?
                """;
        jdbcTemplate.update(sql, failureReason, tenantId, taskId);
    }

    public Optional<AdminExportTaskFile> findTaskFile(UUID tenantId, UUID taskId) {
        String sql = """
                select file_name, content_type, file_content
                from export_task
                where tenant_id = ? and id = ? and task_status = 'SUCCESS'
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AdminExportTaskFile(
                rs.getString("file_name"),
                rs.getString("content_type"),
                rs.getBytes("file_content")
        ), tenantId, taskId).stream().findFirst();
    }

    private QueryParts taskFilters(UUID tenantId, AdminExportTaskQuery query) {
        QueryParts filters = new QueryParts("");
        filters.append(" and tenant_id = ?");
        filters.add(tenantId);
        if (StringUtils.hasText(query.taskType())) {
            filters.append(" and task_type = ?");
            filters.add(query.taskType().trim());
        }
        if (StringUtils.hasText(query.taskStatus())) {
            filters.append(" and task_status = ?");
            filters.add(query.taskStatus().trim());
        }
        if (StringUtils.hasText(query.keyword())) {
            String keyword = "%" + query.keyword().trim() + "%";
            filters.append(" and (task_name ilike ? or file_name ilike ? or requested_by ilike ?)");
            filters.add(keyword);
            filters.add(keyword);
            filters.add(keyword);
        }
        return filters;
    }

    private AdminExportTaskRecord mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new AdminExportTaskRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("task_type"),
                rs.getString("task_name"),
                rs.getString("task_status"),
                rs.getString("query_param"),
                rs.getString("file_name"),
                rs.getString("content_type"),
                intObject(rs, "row_count"),
                intObject(rs, "file_size_bytes"),
                rs.getString("failure_reason"),
                rs.getString("requested_by"),
                rs.getInt("retry_count"),
                instant(rs, "started_at"),
                instant(rs, "completed_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private Integer intObject(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toInstant();
    }

    private static final class QueryParts {
        private final StringBuilder sql;
        private final List<Object> args = new ArrayList<>();

        private QueryParts(String sql) {
            this.sql = new StringBuilder(sql);
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

        private String sql() {
            return sql.toString();
        }

        private Object[] args() {
            return args.toArray();
        }

        private List<Object> argsList() {
            return args;
        }
    }
}
