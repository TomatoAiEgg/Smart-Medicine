package com.zhyf.order.infrastructure;

import com.zhyf.order.application.AdminOrderListItem;
import com.zhyf.order.application.AdminOrderPage;
import com.zhyf.order.application.AdminOrderDetail;
import com.zhyf.order.application.AdminOrderSearchQuery;
import com.zhyf.order.domain.InstitutionApp;
import com.zhyf.order.domain.OrderProgressSnapshot;
import com.zhyf.order.domain.OrderSnapshot;
import com.zhyf.order.domain.WorkflowTaskSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    private static final String OUTBOX_TOPIC = "zhyf-order-event";
    private static final String OUTBOX_SOURCE = "order-service";

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

    public Optional<AdminOrderDetail> findAdminOrderDetailByOrderNo(String orderNo) {
        String sql = """
                select
                    o.id as order_id,
                    o.tenant_id,
                    o.institution_id,
                    i.institution_name,
                    i.storage_type,
                    o.order_no,
                    o.external_order_no,
                    o.status as order_status,
                    o.patient_name,
                    o.patient_phone,
                    o.receiver_name,
                    o.receiver_phone,
                    o.receiver_province,
                    o.receiver_city,
                    o.receiver_zone,
                    o.receiver_address,
                    o.address_type,
                    o.delivery_time,
                    o.batch_no,
                    o.order_remark,
                    latest_validation.validation_status,
                    latest_validation.validation_message,
                    latest_validation.validation_created_at,
                    o.created_at,
                    o.updated_at
                from order_main o
                join institution i on i.id = o.institution_id
                left join lateral (
                    select
                        r.validation_status,
                        r.validation_message,
                        r.created_at as validation_created_at
                    from order_validation_record r
                    where r.order_id = o.id
                    order by r.created_at desc
                    limit 1
                ) latest_validation on true
                where o.order_no = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminOrderDetailHeader, orderNo)
                .stream()
                .findFirst()
                .map(header -> new AdminOrderDetail(
                        header.orderId(),
                        header.tenantId(),
                        header.institutionId(),
                        header.institutionName(),
                        header.storageType(),
                        header.orderNo(),
                        header.externalOrderNo(),
                        header.orderStatus(),
                        header.patientName(),
                        header.patientPhone(),
                        header.receiverName(),
                        header.receiverPhone(),
                        header.receiverProvince(),
                        header.receiverCity(),
                        header.receiverZone(),
                        header.receiverAddress(),
                        header.addressType(),
                        header.deliveryTime(),
                        header.batchNo(),
                        header.orderRemark(),
                        header.validationStatus(),
                        header.validationMessage(),
                        header.validationCreatedAt(),
                        header.createdAt(),
                        header.updatedAt(),
                        findAdminOrderDetailPrescriptions(header.orderId())
                ));
    }

    private List<AdminOrderDetail.Prescription> findAdminOrderDetailPrescriptions(UUID orderId) {
        String sql = """
                select
                    p.id as prescription_id,
                    p.prescription_no,
                    p.external_prescription_no,
                    p.prescription_type,
                    p.status as prescription_status,
                    p.hospital_type,
                    p.dose_count,
                    p.decoction_count,
                    p.decoction_unit_price,
                    p.decoction_total_price,
                    p.total_amount,
                    p.doctor_name,
                    p.diagnosis,
                    p.department_name,
                    p.ward_name,
                    p.bed_no,
                    p.medication_method,
                    p.medication_instruction,
                    p.prescription_remark,
                    count(d.id)::int as detail_count,
                    p.created_at
                from prescription p
                left join prescription_detail d on d.prescription_id = p.id
                where p.order_id = ?
                group by p.id, p.prescription_no, p.external_prescription_no, p.prescription_type,
                         p.status, p.hospital_type, p.dose_count, p.decoction_count,
                         p.decoction_unit_price, p.decoction_total_price, p.total_amount,
                         p.doctor_name, p.diagnosis, p.department_name, p.ward_name, p.bed_no,
                         p.medication_method, p.medication_instruction, p.prescription_remark, p.created_at
                order by p.created_at asc, p.prescription_no asc
                """;
        return jdbcTemplate.query(sql, this::mapAdminOrderDetailPrescription, orderId)
                .stream()
                .map(prescription -> new AdminOrderDetail.Prescription(
                        prescription.prescriptionId(),
                        prescription.prescriptionNo(),
                        prescription.externalPrescriptionNo(),
                        prescription.prescriptionType(),
                        prescription.prescriptionStatus(),
                        prescription.hospitalType(),
                        prescription.doseCount(),
                        prescription.decoctionCount(),
                        prescription.decoctionUnitPrice(),
                        prescription.decoctionTotalPrice(),
                        prescription.totalAmount(),
                        prescription.doctorName(),
                        prescription.diagnosis(),
                        prescription.departmentName(),
                        prescription.wardName(),
                        prescription.bedNo(),
                        prescription.medicationMethod(),
                        prescription.medicationInstruction(),
                        prescription.prescriptionRemark(),
                        prescription.detailCount(),
                        prescription.createdAt(),
                        findAdminOrderDetailDrugDetails(prescription.prescriptionId())
                ))
                .toList();
    }

    private List<AdminOrderDetail.DrugDetail> findAdminOrderDetailDrugDetails(UUID prescriptionId) {
        String sql = """
                select
                    id as detail_id,
                    drug_code,
                    drug_name,
                    platform_drug_code,
                    platform_drug_name,
                    drug_specs,
                    drug_origin,
                    dose,
                    unit,
                    special_usage,
                    quantity,
                    unit_price,
                    settlement_unit_price,
                    total_price,
                    settlement_total_price,
                    sort_no,
                    batch_no,
                    remark,
                    validation_tips,
                    created_at
                from prescription_detail
                where prescription_id = ?
                order by sort_no asc, created_at asc
                """;
        return jdbcTemplate.query(sql, this::mapAdminOrderDetailDrugDetail, prescriptionId);
    }

    public AdminOrderPage searchAdminOrders(AdminOrderSearchQuery query) {
        QueryParts filters = adminOrderFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from order_main o
                join prescription p on p.order_id = o.id
                join institution i on i.id = o.institution_id
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select
                    o.id as order_id,
                    o.tenant_id,
                    o.institution_id,
                    i.institution_name,
                    i.storage_type,
                    o.order_no,
                    o.external_order_no,
                    o.status as order_status,
                    o.patient_name,
                    o.patient_phone,
                    o.receiver_name,
                    o.receiver_phone,
                    o.receiver_province,
                    o.receiver_city,
                    o.receiver_zone,
                    o.receiver_address,
                    o.address_type,
                    p.prescription_no as prescription_nos,
                    p.external_prescription_no as external_prescription_nos,
                    coalesce(p.prescription_type, '') as prescription_types,
                    coalesce(p.hospital_type, '') as hospital_types,
                    1 as prescription_count,
                    coalesce(pd.detail_count, 0) as detail_count,
                    p.dose_count,
                    p.total_amount,
                    o.delivery_time,
                    o.batch_no,
                    o.order_remark,
                    latest_shipment.logistics_company,
                    latest_shipment.logistics_no,
                    latest_shipment.logistics_status,
                    latest_shipment.latest_trace_time,
                    o.created_at,
                    o.updated_at
                from order_main o
                join prescription p on p.order_id = o.id
                join institution i on i.id = o.institution_id
                left join lateral (
                    select count(d.id)::int as detail_count
                    from prescription_detail d
                    where d.prescription_id = p.id
                ) pd on true
                left join lateral (
                    select
                        s.logistics_company,
                        s.logistics_no,
                        s.logistics_status,
                        t.trace_time as latest_trace_time
                    from shipment s
                    left join lateral (
                        select st.trace_time
                        from shipment_trace st
                        where st.shipment_id = s.id
                        order by st.created_at desc
                        limit 1
                    ) t on true
                    where s.order_id = o.id
                    order by s.created_at desc
                    limit 1
                ) latest_shipment on true
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by o.created_at desc, p.prescription_no desc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());

        return new AdminOrderPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminOrderListItem, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    private QueryParts adminOrderFilters(AdminOrderSearchQuery query) {
        QueryParts filters = new QueryParts("");
        filters.addRangeFilter("o.created_at", query.startTime(), query.endTime());
        filters.addLikeFilter("i.institution_name", query.institution());
        filters.addLikeFilter("i.storage_type", query.decoctionCenter());
        filters.addEqualsFilter("o.status", query.orderStatus());
        filters.addLikeFilter("o.patient_name", query.patientName());
        filters.addLikeFilter("o.receiver_phone", query.receiverPhone());
        filters.addLikeFilter("o.receiver_province", query.province());
        filters.addEqualsFilter("o.address_type", query.deliveryType());
        filters.addEqualsFilter("p.prescription_type", query.prescriptionType());
        filters.addEqualsFilter("p.hospital_type", query.hospitalType());
        filters.addExistsShipmentLike("s.logistics_company", query.logisticsCompany());
        filters.addLikeFilter("p.external_prescription_no", query.hospitalPrescriptionNo());
        filters.addKeywordFilter(query.keyword());
        return filters;
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
            String receiverProvince,
            String receiverCity,
            String receiverZone,
            String receiverAddress,
            String addressType,
            Instant deliveryTime,
            String batchNo,
            String orderRemark,
            String callbackUrl,
            String rawPayload
    ) {
        String sql = """
                insert into order_main (
                    id, tenant_id, institution_id, order_no, external_order_no, status,
                    patient_name, patient_phone, receiver_name, receiver_phone, receiver_province,
                    receiver_city, receiver_zone, receiver_address, address_type, delivery_time,
                    batch_no, order_remark, callback_url, raw_payload
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        jdbcTemplate.update(sql, id, tenantId, institutionId, orderNo, externalOrderNo, status,
                patientName, patientPhone, receiverName, receiverPhone, receiverProvince, receiverCity, receiverZone,
                receiverAddress, addressType, offsetDateTime(deliveryTime), batchNo, orderRemark, callbackUrl,
                rawPayload);
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
            String prescriptionType,
            String status,
            String hospitalType,
            Integer doseCount,
            Integer decoctionCount,
            BigDecimal decoctionUnitPrice,
            BigDecimal decoctionTotalPrice,
            BigDecimal totalAmount,
            String doctorName,
            String diagnosis,
            String departmentName,
            String wardName,
            String bedNo,
            String medicationMethod,
            String medicationInstruction,
            String prescriptionRemark,
            String rawPayload
    ) {
        String sql = """
                insert into prescription (
                    id, tenant_id, institution_id, order_id, prescription_no,
                    external_prescription_no, prescription_type, status, hospital_type, dose_count,
                    decoction_count, decoction_unit_price, decoction_total_price, total_amount,
                    doctor_name, diagnosis, department_name, ward_name, bed_no, medication_method,
                    medication_instruction, prescription_remark, raw_payload
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        jdbcTemplate.update(sql, id, tenantId, institutionId, orderId, prescriptionNo,
                externalPrescriptionNo, prescriptionType, status, hospitalType, doseCount, decoctionCount,
                decoctionUnitPrice, decoctionTotalPrice, totalAmount, doctorName, diagnosis, departmentName,
                wardName, bedNo, medicationMethod, medicationInstruction, prescriptionRemark, rawPayload);
    }

    public void insertPrescriptionDetail(
            UUID id,
            UUID tenantId,
            UUID prescriptionId,
            String drugCode,
            String drugName,
            String platformDrugCode,
            String platformDrugName,
            String drugSpecs,
            String drugOrigin,
            String dose,
            String unit,
            String specialUsage,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal settlementUnitPrice,
            BigDecimal totalPrice,
            BigDecimal settlementTotalPrice,
            String batchNo,
            String remark,
            String validationTips,
            int sortNo
    ) {
        String sql = """
                insert into prescription_detail (
                    id, tenant_id, prescription_id, drug_code, drug_name, platform_drug_code,
                    platform_drug_name, drug_specs, drug_origin, dose, unit, special_usage, quantity,
                    unit_price, settlement_unit_price, total_price, settlement_total_price,
                    batch_no, remark, validation_tips, sort_no
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, prescriptionId, drugCode, drugName, platformDrugCode, platformDrugName,
                drugSpecs, drugOrigin, dose, unit, specialUsage, quantity, unitPrice, settlementUnitPrice, totalPrice,
                settlementTotalPrice, batchNo, remark, validationTips, sortNo);
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
                    id, tenant_id, event_id, event_type, aggregate_type, aggregate_id, topic, tag, source, payload, status
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'NEW')
                """;
        jdbcTemplate.update(
                sql,
                id,
                tenantId,
                eventId,
                eventType,
                aggregateType,
                aggregateId,
                OUTBOX_TOPIC,
                eventType,
                OUTBOX_SOURCE,
                payload
        );
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

    private AdminOrderListItem mapAdminOrderListItem(ResultSet rs, int rowNum) throws SQLException {
        return new AdminOrderListItem(
                rs.getObject("order_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("institution_name"),
                rs.getString("storage_type"),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("order_status"),
                rs.getString("patient_name"),
                rs.getString("patient_phone"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("receiver_province"),
                rs.getString("receiver_city"),
                rs.getString("receiver_zone"),
                rs.getString("receiver_address"),
                rs.getString("address_type"),
                rs.getString("prescription_nos"),
                rs.getString("external_prescription_nos"),
                rs.getString("prescription_types"),
                rs.getString("hospital_types"),
                rs.getInt("prescription_count"),
                rs.getInt("detail_count"),
                integer(rs, "dose_count"),
                rs.getBigDecimal("total_amount"),
                instant(rs, "delivery_time"),
                rs.getString("batch_no"),
                rs.getString("order_remark"),
                rs.getString("logistics_company"),
                rs.getString("logistics_no"),
                rs.getString("logistics_status"),
                instant(rs, "latest_trace_time"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminOrderDetailHeader mapAdminOrderDetailHeader(ResultSet rs, int rowNum) throws SQLException {
        return new AdminOrderDetailHeader(
                rs.getObject("order_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("institution_name"),
                rs.getString("storage_type"),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("order_status"),
                rs.getString("patient_name"),
                rs.getString("patient_phone"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("receiver_province"),
                rs.getString("receiver_city"),
                rs.getString("receiver_zone"),
                rs.getString("receiver_address"),
                rs.getString("address_type"),
                instant(rs, "delivery_time"),
                rs.getString("batch_no"),
                rs.getString("order_remark"),
                rs.getString("validation_status"),
                rs.getString("validation_message"),
                instant(rs, "validation_created_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminOrderDetail.Prescription mapAdminOrderDetailPrescription(ResultSet rs, int rowNum)
            throws SQLException {
        return new AdminOrderDetail.Prescription(
                rs.getObject("prescription_id", UUID.class),
                rs.getString("prescription_no"),
                rs.getString("external_prescription_no"),
                rs.getString("prescription_type"),
                rs.getString("prescription_status"),
                rs.getString("hospital_type"),
                integer(rs, "dose_count"),
                integer(rs, "decoction_count"),
                rs.getBigDecimal("decoction_unit_price"),
                rs.getBigDecimal("decoction_total_price"),
                rs.getBigDecimal("total_amount"),
                rs.getString("doctor_name"),
                rs.getString("diagnosis"),
                rs.getString("department_name"),
                rs.getString("ward_name"),
                rs.getString("bed_no"),
                rs.getString("medication_method"),
                rs.getString("medication_instruction"),
                rs.getString("prescription_remark"),
                rs.getInt("detail_count"),
                instant(rs, "created_at"),
                List.of()
        );
    }

    private AdminOrderDetail.DrugDetail mapAdminOrderDetailDrugDetail(ResultSet rs, int rowNum) throws SQLException {
        return new AdminOrderDetail.DrugDetail(
                rs.getObject("detail_id", UUID.class),
                rs.getString("drug_code"),
                rs.getString("drug_name"),
                rs.getString("platform_drug_code"),
                rs.getString("platform_drug_name"),
                rs.getString("drug_specs"),
                rs.getString("drug_origin"),
                rs.getString("dose"),
                rs.getString("unit"),
                rs.getString("special_usage"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("settlement_unit_price"),
                rs.getBigDecimal("total_price"),
                rs.getBigDecimal("settlement_total_price"),
                rs.getInt("sort_no"),
                rs.getString("batch_no"),
                rs.getString("remark"),
                rs.getString("validation_tips"),
                instant(rs, "created_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private record AdminOrderDetailHeader(
            UUID orderId,
            UUID tenantId,
            UUID institutionId,
            String institutionName,
            String storageType,
            String orderNo,
            String externalOrderNo,
            String orderStatus,
            String patientName,
            String patientPhone,
            String receiverName,
            String receiverPhone,
            String receiverProvince,
            String receiverCity,
            String receiverZone,
            String receiverAddress,
            String addressType,
            Instant deliveryTime,
            String batchNo,
            String orderRemark,
            String validationStatus,
            String validationMessage,
            Instant validationCreatedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    private OffsetDateTime offsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private final class QueryParts {
        private final StringBuilder sql;
        private final List<Object> args = new ArrayList<>();

        private QueryParts(String baseSql) {
            this.sql = new StringBuilder(baseSql);
        }

        private void addRangeFilter(String column, Instant from, Instant to) {
            if (from != null) {
                append(" and " + column + " >= ?");
                add(offsetDateTime(from));
            }
            if (to != null) {
                append(" and " + column + " <= ?");
                add(offsetDateTime(to));
            }
        }

        private void addLikeFilter(String column, String value) {
            String trimmed = trimmed(value);
            if (trimmed != null) {
                append(" and " + column + " ilike ?");
                add("%" + trimmed + "%");
            }
        }

        private void addEqualsFilter(String column, String value) {
            String trimmed = trimmed(value);
            if (trimmed != null) {
                append(" and " + column + " = ?");
                add(trimmed);
            }
        }

        private void addExistsShipmentLike(String column, String value) {
            String trimmed = trimmed(value);
            if (trimmed != null) {
                append(" and exists (select 1 from shipment s where s.order_id = o.id and " + column + " ilike ?)");
                add("%" + trimmed + "%");
            }
        }

        private void addKeywordFilter(String value) {
            String trimmed = trimmed(value);
            if (trimmed != null) {
                append("""
                         and (
                            o.order_no ilike ?
                            or o.external_order_no ilike ?
                            or p.prescription_no ilike ?
                            or p.external_prescription_no ilike ?
                        )
                        """);
                String pattern = "%" + trimmed + "%";
                add(pattern);
                add(pattern);
                add(pattern);
                add(pattern);
            }
        }

        private String trimmed(String value) {
            return value == null || value.isBlank() ? null : value.trim();
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

        private List<Object> argsList() {
            return args;
        }

        private Object[] args() {
            return args.toArray();
        }
    }
}
