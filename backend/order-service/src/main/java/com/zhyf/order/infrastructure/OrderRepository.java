package com.zhyf.order.infrastructure;

import com.zhyf.order.domain.InstitutionApp;
import com.zhyf.order.domain.OrderProgressSnapshot;
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

    public Optional<OrderProgressSnapshot> findOrderProgressByOrderNo(String orderNo) {
        String sql = """
                select id as order_id, tenant_id, order_no, external_order_no, status as order_status,
                       created_at, updated_at
                from order_main
                where order_no = ?
                """;
        return jdbcTemplate.query(sql, this::mapOrderProgressHeader, orderNo).stream()
                .findFirst()
                .map(header -> new OrderProgressSnapshot(
                        header.orderId(),
                        header.tenantId(),
                        header.orderNo(),
                        header.externalOrderNo(),
                        header.orderStatus(),
                        header.createdAt(),
                        header.updatedAt(),
                        findPrescriptionProgress(header.orderId()),
                        findWorkflowProgress(header.orderId()),
                        findDispenseProgress(header.orderId()),
                        findDecoctionProgress(header.orderId()),
                        findShipmentProgress(header.orderId()),
                        findCallbackProgress(header.orderId()),
                        findStatusLogProgress(header.orderId())
                ));
    }

    private List<OrderProgressSnapshot.PrescriptionProgress> findPrescriptionProgress(UUID orderId) {
        String sql = """
                select p.id as prescription_id,
                       p.prescription_no,
                       p.external_prescription_no,
                       p.status as prescription_status,
                       count(d.id)::int as detail_count,
                       p.created_at
                from prescription p
                left join prescription_detail d on d.prescription_id = p.id
                where p.order_id = ?
                group by p.id, p.prescription_no, p.external_prescription_no, p.status, p.created_at
                order by p.created_at asc, p.prescription_no asc
                """;
        return jdbcTemplate.query(sql, this::mapPrescriptionProgress, orderId);
    }

    private List<OrderProgressSnapshot.WorkflowProgress> findWorkflowProgress(UUID orderId) {
        String sql = """
                select id as task_id, task_type, task_status, assigned_to, review_comment, created_at, completed_at
                from workflow_task
                where order_id = ?
                order by created_at asc
                """;
        return jdbcTemplate.query(sql, this::mapWorkflowProgress, orderId);
    }

    private List<OrderProgressSnapshot.DispenseProgress> findDispenseProgress(UUID orderId) {
        String sql = """
                select id as record_id, task_id, dispenser, dispense_comment, print_status, dispensed_at
                from dispense_record d
                where d.order_id = ?
                order by d.dispensed_at asc
                """;
        return jdbcTemplate.query(sql, this::mapDispenseProgress, orderId);
    }

    private List<OrderProgressSnapshot.DecoctionProgress> findDecoctionProgress(UUID orderId) {
        String sql = """
                select id as task_id, task_no, prescription_no, device_code, pail_no, task_status,
                       operator, started_at, finished_at, created_at
                from decoction_task
                where order_id = ?
                order by created_at asc
                """;
        return jdbcTemplate.query(sql, this::mapDecoctionProgress, orderId);
    }

    private List<OrderProgressSnapshot.ShipmentProgress> findShipmentProgress(UUID orderId) {
        String sql = """
                select s.id as shipment_id,
                       s.logistics_no,
                       s.logistics_company,
                       s.logistics_status,
                       latest_trace.trace_status as latest_trace_status,
                       latest_trace.trace_content as latest_trace_content,
                       latest_trace.trace_time as latest_trace_time
                from shipment s
                left join lateral (
                    select trace_status, trace_content, trace_time
                    from shipment_trace st
                    where st.shipment_id = s.id
                    order by st.created_at desc
                    limit 1
                ) latest_trace on true
                where s.order_id = ?
                order by s.created_at asc
                """;
        return jdbcTemplate.query(sql, this::mapShipmentProgress, orderId);
    }

    private List<OrderProgressSnapshot.CallbackProgress> findCallbackProgress(UUID orderId) {
        String sql = """
                select id as callback_id, callback_type, business_id, status as callback_status,
                       retry_count, next_retry_at, updated_at
                from callback_record
                where order_id = ?
                order by updated_at desc, created_at desc
                """;
        return jdbcTemplate.query(sql, this::mapCallbackProgress, orderId);
    }

    private List<OrderProgressSnapshot.StatusLogProgress> findStatusLogProgress(UUID orderId) {
        String sql = """
                select id as log_id, from_status, to_status, operator_type, source, created_at
                from order_status_log
                where order_id = ?
                order by created_at asc
                """;
        return jdbcTemplate.query(sql, this::mapStatusLogProgress, orderId);
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

    private OrderProgressSnapshot mapOrderProgressHeader(ResultSet rs, int rowNum) throws SQLException {
        return new OrderProgressSnapshot(
                rs.getObject("order_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("order_status"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private OrderProgressSnapshot.PrescriptionProgress mapPrescriptionProgress(ResultSet rs, int rowNum)
            throws SQLException {
        return new OrderProgressSnapshot.PrescriptionProgress(
                rs.getObject("prescription_id", UUID.class),
                rs.getString("prescription_no"),
                rs.getString("external_prescription_no"),
                rs.getString("prescription_status"),
                rs.getInt("detail_count"),
                instant(rs, "created_at")
        );
    }

    private OrderProgressSnapshot.WorkflowProgress mapWorkflowProgress(ResultSet rs, int rowNum) throws SQLException {
        return new OrderProgressSnapshot.WorkflowProgress(
                rs.getObject("task_id", UUID.class),
                rs.getString("task_type"),
                rs.getString("task_status"),
                rs.getString("assigned_to"),
                rs.getString("review_comment"),
                instant(rs, "created_at"),
                instant(rs, "completed_at")
        );
    }

    private OrderProgressSnapshot.DispenseProgress mapDispenseProgress(ResultSet rs, int rowNum) throws SQLException {
        return new OrderProgressSnapshot.DispenseProgress(
                rs.getObject("record_id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getString("dispenser"),
                rs.getString("dispense_comment"),
                rs.getString("print_status"),
                instant(rs, "dispensed_at")
        );
    }

    private OrderProgressSnapshot.DecoctionProgress mapDecoctionProgress(ResultSet rs, int rowNum)
            throws SQLException {
        return new OrderProgressSnapshot.DecoctionProgress(
                rs.getObject("task_id", UUID.class),
                rs.getString("task_no"),
                rs.getString("prescription_no"),
                rs.getString("device_code"),
                rs.getString("pail_no"),
                rs.getString("task_status"),
                rs.getString("operator"),
                instant(rs, "started_at"),
                instant(rs, "finished_at"),
                instant(rs, "created_at")
        );
    }

    private OrderProgressSnapshot.ShipmentProgress mapShipmentProgress(ResultSet rs, int rowNum)
            throws SQLException {
        return new OrderProgressSnapshot.ShipmentProgress(
                rs.getObject("shipment_id", UUID.class),
                rs.getString("logistics_no"),
                rs.getString("logistics_company"),
                rs.getString("logistics_status"),
                rs.getString("latest_trace_status"),
                rs.getString("latest_trace_content"),
                instant(rs, "latest_trace_time")
        );
    }

    private OrderProgressSnapshot.CallbackProgress mapCallbackProgress(ResultSet rs, int rowNum)
            throws SQLException {
        return new OrderProgressSnapshot.CallbackProgress(
                rs.getObject("callback_id", UUID.class),
                rs.getString("callback_type"),
                rs.getString("business_id"),
                rs.getString("callback_status"),
                rs.getInt("retry_count"),
                instant(rs, "next_retry_at"),
                instant(rs, "updated_at")
        );
    }

    private OrderProgressSnapshot.StatusLogProgress mapStatusLogProgress(ResultSet rs, int rowNum)
            throws SQLException {
        return new OrderProgressSnapshot.StatusLogProgress(
                rs.getObject("log_id", UUID.class),
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("operator_type"),
                rs.getString("source"),
                instant(rs, "created_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
