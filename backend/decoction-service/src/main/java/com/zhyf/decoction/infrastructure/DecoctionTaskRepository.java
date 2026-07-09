package com.zhyf.decoction.infrastructure;

import com.zhyf.decoction.domain.DecoctionTaskSnapshot;
import com.zhyf.decoction.domain.PrescriptionForDecoction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DecoctionTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public DecoctionTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PrescriptionForDecoction> findCanOperatePrescriptions(int limit) {
        String sql = """
                select
                    p.tenant_id,
                    p.order_id,
                    p.id as prescription_id,
                    o.order_no,
                    o.external_order_no,
                    p.prescription_no,
                    o.status as order_status
                from prescription p
                join order_main o on o.id = p.order_id
                where o.status = 'RECHECKED'
                  and not exists (
                      select 1
                      from decoction_task t
                      where t.prescription_id = p.id
                        and t.task_status in ('BOUND', 'DECOCTING', 'DECOCTED')
                  )
                order by o.created_at asc
                limit ?
                """;
        return jdbcTemplate.query(sql, this::mapPrescriptionForDecoction, limit);
    }

    public Optional<PrescriptionForDecoction> findPrescription(String prescriptionNo) {
        String sql = """
                select
                    p.tenant_id,
                    p.order_id,
                    p.id as prescription_id,
                    o.order_no,
                    o.external_order_no,
                    p.prescription_no,
                    o.status as order_status
                from prescription p
                join order_main o on o.id = p.order_id
                where p.prescription_no = ?
                """;
        return jdbcTemplate.query(sql, this::mapPrescriptionForDecoction, prescriptionNo)
                .stream()
                .findFirst();
    }

    public int createTask(
            UUID taskId,
            String taskNo,
            UUID tenantId,
            UUID orderId,
            UUID prescriptionId,
            String prescriptionNo,
            String deviceCode,
            String pailNo,
            String taskStatus,
            String bindOperationId,
            String operator
    ) {
        String sql = """
                insert into decoction_task (
                    id, task_no, tenant_id, order_id, prescription_id, prescription_no,
                    device_code, pail_no, task_status, bind_operation_id, operator
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return jdbcTemplate.update(
                sql,
                taskId,
                taskNo,
                tenantId,
                orderId,
                prescriptionId,
                prescriptionNo,
                deviceCode,
                pailNo,
                taskStatus,
                bindOperationId,
                operator
        );
    }

    public int markStarted(UUID taskId, String operationId, String operator) {
        String sql = """
                update decoction_task
                set task_status = 'DECOCTING',
                    start_operation_id = ?,
                    operator = ?,
                    started_at = now(),
                    updated_at = now()
                where id = ?
                  and task_status = 'BOUND'
                  and start_operation_id is null
                """;
        return jdbcTemplate.update(sql, operationId, operator, taskId);
    }

    public int markFinished(UUID taskId, String operationId, String operator) {
        String sql = """
                update decoction_task
                set task_status = 'DECOCTED',
                    finish_operation_id = ?,
                    operator = ?,
                    finished_at = now(),
                    updated_at = now()
                where id = ?
                  and task_status = 'DECOCTING'
                  and finish_operation_id is null
                """;
        return jdbcTemplate.update(sql, operationId, operator, taskId);
    }

    public int markCancelled(UUID taskId, String operationId, String operator) {
        String sql = """
                update decoction_task
                set task_status = 'CANCELLED',
                    cancel_operation_id = ?,
                    operator = ?,
                    cancelled_at = now(),
                    updated_at = now()
                where id = ?
                  and task_status = 'BOUND'
                  and cancel_operation_id is null
                """;
        return jdbcTemplate.update(sql, operationId, operator, taskId);
    }

    public int markTerminated(UUID taskId, String operationId, String operator) {
        String sql = """
                update decoction_task
                set task_status = 'TERMINATED',
                    terminate_operation_id = ?,
                    operator = ?,
                    terminated_at = now(),
                    updated_at = now()
                where id = ?
                  and task_status = 'DECOCTING'
                  and terminate_operation_id is null
                """;
        return jdbcTemplate.update(sql, operationId, operator, taskId);
    }

    public List<DecoctionTaskSnapshot> findActiveTasks() {
        String sql = baseTaskQuery() + """
                where t.task_status in ('BOUND', 'DECOCTING')
                order by t.created_at asc
                """;
        return jdbcTemplate.query(sql, this::mapTaskSnapshot);
    }

    public List<DecoctionTaskSnapshot> findTasksByStatus(String taskStatus) {
        String sql = baseTaskQuery() + """
                where t.task_status = ?
                order by t.created_at asc
                """;
        return jdbcTemplate.query(sql, this::mapTaskSnapshot, taskStatus);
    }

    public Optional<DecoctionTaskSnapshot> findByTaskId(UUID taskId) {
        String sql = baseTaskQuery() + """
                where t.id = ?
                """;
        return jdbcTemplate.query(sql, this::mapTaskSnapshot, taskId).stream().findFirst();
    }

    public Optional<DecoctionTaskSnapshot> findByTaskNo(String taskNo) {
        String sql = baseTaskQuery() + """
                where t.task_no = ?
                """;
        return jdbcTemplate.query(sql, this::mapTaskSnapshot, taskNo).stream().findFirst();
    }

    public Optional<DecoctionTaskSnapshot> findByBindOperationId(String operationId) {
        String sql = baseTaskQuery() + """
                where t.bind_operation_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapTaskSnapshot, operationId).stream().findFirst();
    }

    public Optional<DecoctionTaskSnapshot> findByStartOperationId(String operationId) {
        String sql = baseTaskQuery() + """
                where t.start_operation_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapTaskSnapshot, operationId).stream().findFirst();
    }

    public Optional<DecoctionTaskSnapshot> findByFinishOperationId(String operationId) {
        String sql = baseTaskQuery() + """
                where t.finish_operation_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapTaskSnapshot, operationId).stream().findFirst();
    }

    public Optional<DecoctionTaskSnapshot> findByCancelOperationId(String operationId) {
        String sql = baseTaskQuery() + """
                where t.cancel_operation_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapTaskSnapshot, operationId).stream().findFirst();
    }

    public Optional<DecoctionTaskSnapshot> findByTerminateOperationId(String operationId) {
        String sql = baseTaskQuery() + """
                where t.terminate_operation_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapTaskSnapshot, operationId).stream().findFirst();
    }

    public int createTaskEvent(
            UUID eventId,
            UUID taskId,
            UUID tenantId,
            UUID orderId,
            String eventType,
            String operationId,
            String operator,
            String eventPayload,
            Instant eventTime
    ) {
        String sql = """
                insert into decoction_task_event (
                    id, task_id, tenant_id, order_id, event_type,
                    operation_id, operator, event_payload, event_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """;
        return jdbcTemplate.update(
                sql,
                eventId,
                taskId,
                tenantId,
                orderId,
                eventType,
                operationId,
                operator,
                eventPayload,
                offsetDateTime(eventTime)
        );
    }

    public int createDeviceWorkRecord(
            UUID recordId,
            DecoctionTaskSnapshot task,
            String actionType,
            String actionResult,
            String taskStatusBefore,
            String taskStatusAfter,
            String operationId,
            String source,
            String operator,
            String detailPayload,
            Instant actionTime
    ) {
        String sql = """
                insert into decoction_device_work_record (
                    id, task_id, tenant_id, order_id, task_no, prescription_no,
                    device_code, pail_no, action_type, action_result,
                    task_status_before, task_status_after, operation_id, source,
                    operator, detail_payload, action_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """;
        return jdbcTemplate.update(
                sql,
                recordId,
                task.taskId(),
                task.tenantId(),
                task.orderId(),
                task.taskNo(),
                task.prescriptionNo(),
                task.deviceCode(),
                task.pailNo(),
                actionType,
                actionResult,
                taskStatusBefore,
                taskStatusAfter,
                operationId,
                source,
                operator,
                detailPayload,
                offsetDateTime(actionTime)
        );
    }

    public Optional<DeviceWorkRecordSnapshot> findDeviceWorkRecordByOperationId(String operationId) {
        String sql = baseDeviceWorkRecordQuery() + """
                where r.operation_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapDeviceWorkRecordSnapshot, operationId).stream().findFirst();
    }

    public List<DeviceWorkRecordSnapshot> findDeviceWorkRecordsByTaskNo(String taskNo) {
        String sql = baseDeviceWorkRecordQuery() + """
                where r.task_no = ?
                order by r.created_at desc
                """;
        return jdbcTemplate.query(sql, this::mapDeviceWorkRecordSnapshot, taskNo);
    }

    public Optional<DecoctionTaskEventSnapshot> findEventByOperationId(String operationId) {
        String sql = baseEventQuery() + """
                where e.operation_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapTaskEventSnapshot, operationId).stream().findFirst();
    }

    public List<DecoctionTaskEventSnapshot> findEventsByTaskNo(String taskNo) {
        String sql = baseEventQuery() + """
                where t.task_no = ?
                order by e.created_at desc
                """;
        return jdbcTemplate.query(sql, this::mapTaskEventSnapshot, taskNo);
    }

    public Optional<DecoctionTaskSnapshot> findActiveTaskByPrescriptionId(UUID prescriptionId) {
        String sql = baseTaskQuery() + """
                where t.prescription_id = ?
                  and t.task_status in ('BOUND', 'DECOCTING')
                """;
        return jdbcTemplate.query(sql, this::mapTaskSnapshot, prescriptionId).stream().findFirst();
    }

    public Optional<DecoctionTaskSnapshot> findActiveTaskByPrescriptionNo(String prescriptionNo) {
        String sql = baseTaskQuery() + """
                where t.prescription_no = ?
                  and t.task_status in ('BOUND', 'DECOCTING')
                """;
        return jdbcTemplate.query(sql, this::mapTaskSnapshot, prescriptionNo).stream().findFirst();
    }

    public Optional<DecoctionTaskSnapshot> findActiveTaskByPailNo(String pailNo) {
        String sql = baseTaskQuery() + """
                where t.pail_no = ?
                  and t.task_status in ('BOUND', 'DECOCTING')
                """;
        return jdbcTemplate.query(sql, this::mapTaskSnapshot, pailNo).stream().findFirst();
    }

    public Optional<DecoctionTaskSnapshot> findActiveTaskByDeviceCode(String deviceCode) {
        String sql = baseTaskQuery() + """
                where t.device_code = ?
                  and t.task_status in ('BOUND', 'DECOCTING')
                """;
        return jdbcTemplate.query(sql, this::mapTaskSnapshot, deviceCode).stream().findFirst();
    }

    private String baseTaskQuery() {
        return """
                select
                    t.id as task_id,
                    t.task_no,
                    t.tenant_id,
                    t.order_id,
                    t.prescription_id,
                    o.order_no,
                    t.prescription_no,
                    t.device_code,
                    t.pail_no,
                    t.task_status,
                    t.operator,
                    t.started_at,
                    t.finished_at,
                    t.created_at,
                    t.updated_at
                from decoction_task t
                join order_main o on o.id = t.order_id
                """;
    }

    private String baseDeviceWorkRecordQuery() {
        return """
                select
                    r.id as record_id,
                    r.task_id,
                    r.task_no,
                    r.tenant_id,
                    r.order_id,
                    r.prescription_no,
                    r.device_code,
                    r.pail_no,
                    r.action_type,
                    r.action_result,
                    r.task_status_before,
                    r.task_status_after,
                    r.operation_id,
                    r.source,
                    r.operator,
                    r.detail_payload::text as detail_payload,
                    r.action_time,
                    r.created_at
                from decoction_device_work_record r
                """;
    }

    private String baseEventQuery() {
        return """
                select
                    e.id as event_id,
                    e.task_id,
                    t.task_no,
                    e.tenant_id,
                    e.order_id,
                    e.event_type,
                    e.operation_id,
                    e.operator,
                    e.event_payload::text as event_payload,
                    e.event_time,
                    e.created_at
                from decoction_task_event e
                join decoction_task t on t.id = e.task_id
                """;
    }

    private PrescriptionForDecoction mapPrescriptionForDecoction(ResultSet rs, int rowNum) throws SQLException {
        return new PrescriptionForDecoction(
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getObject("prescription_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("prescription_no"),
                rs.getString("order_status")
        );
    }

    private DecoctionTaskSnapshot mapTaskSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new DecoctionTaskSnapshot(
                rs.getObject("task_id", UUID.class),
                rs.getString("task_no"),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getObject("prescription_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("prescription_no"),
                rs.getString("device_code"),
                rs.getString("pail_no"),
                rs.getString("task_status"),
                rs.getString("operator"),
                instant(rs, "started_at"),
                instant(rs, "finished_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private DecoctionTaskEventSnapshot mapTaskEventSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new DecoctionTaskEventSnapshot(
                rs.getObject("event_id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getString("task_no"),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("operation_id"),
                rs.getString("operator"),
                rs.getString("event_payload"),
                instant(rs, "event_time"),
                instant(rs, "created_at")
        );
    }

    private DeviceWorkRecordSnapshot mapDeviceWorkRecordSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new DeviceWorkRecordSnapshot(
                rs.getObject("record_id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getString("task_no"),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("prescription_no"),
                rs.getString("device_code"),
                rs.getString("pail_no"),
                rs.getString("action_type"),
                rs.getString("action_result"),
                rs.getString("task_status_before"),
                rs.getString("task_status_after"),
                rs.getString("operation_id"),
                rs.getString("source"),
                rs.getString("operator"),
                rs.getString("detail_payload"),
                instant(rs, "action_time"),
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

    public record DecoctionTaskEventSnapshot(
            UUID eventId,
            UUID taskId,
            String taskNo,
            UUID tenantId,
            UUID orderId,
            String eventType,
            String operationId,
            String operator,
            String eventPayload,
            Instant eventTime,
            Instant createdAt
    ) {
    }

    public record DeviceWorkRecordSnapshot(
            UUID recordId,
            UUID taskId,
            String taskNo,
            UUID tenantId,
            UUID orderId,
            String prescriptionNo,
            String deviceCode,
            String pailNo,
            String actionType,
            String actionResult,
            String taskStatusBefore,
            String taskStatusAfter,
            String operationId,
            String source,
            String operator,
            String detailPayload,
            Instant actionTime,
            Instant createdAt
    ) {
    }
}
