package com.zhyf.workflow.infrastructure;

import com.zhyf.workflow.domain.WorkflowTaskSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public WorkflowTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WorkflowTaskSnapshot> findPendingReviewTasks() {
        String sql = baseTaskQuery() + """
                where t.task_type = 'ORDER_REVIEW' and t.task_status = 'PENDING'
                order by t.created_at asc
                """;
        return jdbcTemplate.query(sql, this::mapWorkflowTaskSnapshot);
    }

    public List<WorkflowTaskSnapshot> findPendingTasksByType(String taskType) {
        String sql = baseTaskQuery() + """
                where t.task_type = ? and t.task_status = 'PENDING'
                order by t.created_at asc
                """;
        return jdbcTemplate.query(sql, this::mapWorkflowTaskSnapshot, taskType);
    }

    public Optional<WorkflowTaskSnapshot> findReviewTaskById(UUID taskId) {
        String sql = baseTaskQuery() + """
                where t.id = ?
                """;
        return jdbcTemplate.query(sql, this::mapWorkflowTaskSnapshot, taskId).stream().findFirst();
    }

    public int createOrderReviewTask(UUID taskId, UUID tenantId, UUID orderId, String sourceEventId, String payload) {
        String sql = """
                insert into workflow_task (
                    id, tenant_id, order_id, task_type, task_status, source_event_id, payload
                ) values (?, ?, ?, 'ORDER_REVIEW', 'PENDING', ?, ?::jsonb)
                on conflict (source_event_id, task_type) do nothing
                """;
        return jdbcTemplate.update(sql, taskId, tenantId, orderId, sourceEventId, payload);
    }

    public int createWorkflowTask(
            UUID taskId,
            UUID tenantId,
            UUID orderId,
            String taskType,
            String sourceEventId,
            String payload
    ) {
        String sql = """
                insert into workflow_task (
                    id, tenant_id, order_id, task_type, task_status, source_event_id, payload
                ) values (?, ?, ?, ?, 'PENDING', ?, ?::jsonb)
                on conflict (source_event_id, task_type) do nothing
                """;
        return jdbcTemplate.update(sql, taskId, tenantId, orderId, taskType, sourceEventId, payload);
    }

    public int updateWorkflowTaskReviewResult(
            UUID taskId,
            String taskStatus,
            String reviewer,
            String reviewComment
    ) {
        String sql = """
                update workflow_task
                set task_status = ?,
                    assigned_to = ?,
                    review_comment = ?,
                    completed_at = now(),
                    updated_at = now()
                where id = ? and task_status = 'PENDING'
                """;
        return jdbcTemplate.update(sql, taskStatus, reviewer, reviewComment, taskId);
    }

    private String baseTaskQuery() {
        return """
                select
                    t.id as task_id,
                    t.tenant_id,
                    t.order_id,
                    t.task_type,
                    t.task_status,
                    t.source_event_id,
                    t.assigned_to,
                    t.review_comment,
                    o.order_no,
                    o.external_order_no,
                    o.status as order_status,
                    v.validation_status,
                    v.validation_message,
                    t.created_at,
                    t.updated_at,
                    t.completed_at
                from workflow_task t
                join order_main o on o.id = t.order_id
                left join lateral (
                    select validation_status, validation_message
                    from order_validation_record r
                    where r.order_id = t.order_id
                    order by r.created_at desc
                    limit 1
                ) v on true
                """;
    }

    private WorkflowTaskSnapshot mapWorkflowTaskSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowTaskSnapshot(
                rs.getObject("task_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("task_type"),
                rs.getString("task_status"),
                rs.getString("source_event_id"),
                rs.getString("assigned_to"),
                rs.getString("review_comment"),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("order_status"),
                rs.getString("validation_status"),
                rs.getString("validation_message"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "completed_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
