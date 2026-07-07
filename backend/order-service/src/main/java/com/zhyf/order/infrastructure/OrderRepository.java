package com.zhyf.order.infrastructure;

import com.zhyf.order.domain.InstitutionApp;
import com.zhyf.order.domain.OrderSnapshot;
import com.zhyf.order.domain.WorkflowTaskSnapshot;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<InstitutionApp> findEnabledApp(String appKey) {
        String sql = """
                select tenant_id, institution_id, app_key, app_secret, callback_url
                from institution_app
                where app_key = ? and enabled = true
                """;
        return jdbcTemplate.query(sql, this::mapInstitutionApp, appKey).stream().findFirst();
    }

    public Optional<OrderSnapshot> findOrderByExternalNo(UUID tenantId, UUID institutionId, String externalOrderNo) {
        String sql = """
                select id, tenant_id, institution_id, order_no, external_order_no, status, created_at
                from order_main
                where tenant_id = ? and institution_id = ? and external_order_no = ?
                """;
        return jdbcTemplate.query(sql, this::mapOrderSnapshot, tenantId, institutionId, externalOrderNo)
                .stream()
                .findFirst();
    }

    public Optional<OrderSnapshot> findOrderByOrderNo(String orderNo) {
        String sql = """
                select id, tenant_id, institution_id, order_no, external_order_no, status, created_at
                from order_main
                where order_no = ?
                """;
        return jdbcTemplate.query(sql, this::mapOrderSnapshot, orderNo).stream().findFirst();
    }

    public Optional<OrderSnapshot> findOrderById(UUID orderId) {
        String sql = """
                select id, tenant_id, institution_id, order_no, external_order_no, status, created_at
                from order_main
                where id = ?
                """;
        return jdbcTemplate.query(sql, this::mapOrderSnapshot, orderId).stream().findFirst();
    }

    public List<WorkflowTaskSnapshot> findPendingReviewTasks() {
        String sql = """
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
                where t.task_type = 'ORDER_REVIEW' and t.task_status = 'PENDING'
                order by t.created_at asc
                """;
        return jdbcTemplate.query(sql, this::mapWorkflowTaskSnapshot);
    }

    public Optional<WorkflowTaskSnapshot> findReviewTaskById(UUID taskId) {
        String sql = """
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
                where t.id = ?
                """;
        return jdbcTemplate.query(sql, this::mapWorkflowTaskSnapshot, taskId).stream().findFirst();
    }

    public void insertOrder(
            UUID id,
            UUID tenantId,
            UUID institutionId,
            String orderNo,
            String externalOrderNo,
            String status,
            String patientName,
            String patientPhone,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            String callbackUrl,
            String rawPayload
    ) {
        String sql = """
                insert into order_main (
                    id, tenant_id, institution_id, order_no, external_order_no, status,
                    patient_name, patient_phone, receiver_name, receiver_phone, receiver_address,
                    callback_url, raw_payload
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        jdbcTemplate.update(sql, id, tenantId, institutionId, orderNo, externalOrderNo, status,
                patientName, patientPhone, receiverName, receiverPhone, receiverAddress, callbackUrl, rawPayload);
    }

    public void insertOrderStatusLog(
            UUID id,
            UUID tenantId,
            UUID orderId,
            String fromStatus,
            String toStatus,
            String operatorType,
            String source
    ) {
        String sql = """
                insert into order_status_log (
                    id, tenant_id, order_id, from_status, to_status, operator_type, source
                ) values (?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, orderId, fromStatus, toStatus, operatorType, source);
    }

    public void insertPrescription(
            UUID id,
            UUID tenantId,
            UUID institutionId,
            UUID orderId,
            String prescriptionNo,
            String externalPrescriptionNo,
            String status,
            String doctorName,
            String diagnosis,
            String rawPayload
    ) {
        String sql = """
                insert into prescription (
                    id, tenant_id, institution_id, order_id, prescription_no,
                    external_prescription_no, status, doctor_name, diagnosis, raw_payload
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        jdbcTemplate.update(sql, id, tenantId, institutionId, orderId, prescriptionNo,
                externalPrescriptionNo, status, doctorName, diagnosis, rawPayload);
    }

    public void insertPrescriptionDetail(
            UUID id,
            UUID tenantId,
            UUID prescriptionId,
            String drugCode,
            String drugName,
            String dose,
            String unit,
            int sortNo
    ) {
        String sql = """
                insert into prescription_detail (
                    id, tenant_id, prescription_id, drug_code, drug_name, dose, unit, sort_no
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, prescriptionId, drugCode, drugName, dose, unit, sortNo);
    }

    public int updateOrderStatus(UUID orderId, String status) {
        String sql = """
                update order_main
                set status = ?, updated_at = now()
                where id = ?
                """;
        return jdbcTemplate.update(sql, status, orderId);
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

    public void insertOutbox(
            UUID id,
            UUID tenantId,
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String payload
    ) {
        String sql = """
                insert into event_outbox (
                    id, tenant_id, event_id, event_type, aggregate_type, aggregate_id, payload, status
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, 'NEW')
                """;
        jdbcTemplate.update(sql, id, tenantId, eventId, eventType, aggregateType, aggregateId, payload);
    }

    private InstitutionApp mapInstitutionApp(ResultSet rs, int rowNum) throws SQLException {
        return new InstitutionApp(
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("app_key"),
                rs.getString("app_secret"),
                rs.getString("callback_url")
        );
    }

    private OrderSnapshot mapOrderSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new OrderSnapshot(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("status"),
                instant(rs, "created_at")
        );
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
