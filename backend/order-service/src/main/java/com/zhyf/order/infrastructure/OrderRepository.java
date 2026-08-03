package com.zhyf.order.infrastructure;

import com.zhyf.order.application.AdminOrderListItem;
import com.zhyf.order.application.AdminInstitutionApiPermissionPage;
import com.zhyf.order.application.AdminInstitutionApiPermissionQuery;
import com.zhyf.order.application.AdminInstitutionApiPermissionRecord;
import com.zhyf.order.application.AdminInstitutionApiPage;
import com.zhyf.order.application.AdminInstitutionApiQuery;
import com.zhyf.order.application.AdminInstitutionApiRecord;
import com.zhyf.order.application.AdminInstitutionAppPage;
import com.zhyf.order.application.AdminInstitutionAppQuery;
import com.zhyf.order.application.AdminInstitutionAppRecord;
import com.zhyf.order.application.AdminDictItemPage;
import com.zhyf.order.application.AdminDictItemQuery;
import com.zhyf.order.application.AdminDictItemRecord;
import com.zhyf.order.application.AdminDictTypePage;
import com.zhyf.order.application.AdminDictTypeQuery;
import com.zhyf.order.application.AdminDictTypeRecord;
import com.zhyf.order.application.AdminDecoctCenterPage;
import com.zhyf.order.application.AdminDecoctCenterQuery;
import com.zhyf.order.application.AdminDecoctCenterRecord;
import com.zhyf.order.application.AdminHerbPage;
import com.zhyf.order.application.AdminHerbQuery;
import com.zhyf.order.application.AdminHerbRecord;
import com.zhyf.order.application.AdminHerbAreaPage;
import com.zhyf.order.application.AdminHerbAreaQuery;
import com.zhyf.order.application.AdminHerbAreaRecord;
import com.zhyf.order.application.AdminHerbIndexPage;
import com.zhyf.order.application.AdminHerbIndexQuery;
import com.zhyf.order.application.AdminHerbIndexRecord;
import com.zhyf.order.application.AdminHerbIndexOperationLogPage;
import com.zhyf.order.application.AdminHerbIndexOperationLogQuery;
import com.zhyf.order.application.AdminHerbIndexOperationLogRecord;
import com.zhyf.order.application.AdminSystemConfigPage;
import com.zhyf.order.application.AdminSystemConfigQuery;
import com.zhyf.order.application.AdminSystemConfigRecord;
import com.zhyf.order.application.AdminInstitutionIpWhitelistPage;
import com.zhyf.order.application.AdminInstitutionIpWhitelistQuery;
import com.zhyf.order.application.AdminInstitutionIpWhitelistRecord;
import com.zhyf.order.application.AdminInstitutionPage;
import com.zhyf.order.application.AdminInstitutionQuery;
import com.zhyf.order.application.AdminInstitutionRecord;
import com.zhyf.order.application.AdminLabelTemplatePage;
import com.zhyf.order.application.AdminLabelTemplateQuery;
import com.zhyf.order.application.AdminLabelTemplateRecord;
import com.zhyf.order.application.AdminLabelPrintRecord;
import com.zhyf.order.application.AdminLabelPrintRecordPage;
import com.zhyf.order.application.AdminLabelPrintRecordQuery;
import com.zhyf.order.application.AdminLogisticsAddressCostPage;
import com.zhyf.order.application.AdminLogisticsAddressCostQuery;
import com.zhyf.order.application.AdminLogisticsAddressCostRecord;
import com.zhyf.order.application.AdminLogisticsSpecialRulePage;
import com.zhyf.order.application.AdminLogisticsSpecialRuleQuery;
import com.zhyf.order.application.AdminLogisticsSpecialRuleRecord;
import com.zhyf.order.application.AdminManualProcessItem;
import com.zhyf.order.application.AdminManualProcessPage;
import com.zhyf.order.application.AdminManualProcessQuery;
import com.zhyf.order.application.AdminOrderPage;
import com.zhyf.order.application.AdminOrderMergeCandidate;
import com.zhyf.order.application.AdminOrderMergePage;
import com.zhyf.order.application.AdminOrderMergeQuery;
import com.zhyf.order.application.AdminOrderMergeRecord;
import com.zhyf.order.application.AdminOrderInterceptRulePage;
import com.zhyf.order.application.AdminOrderInterceptRuleQuery;
import com.zhyf.order.application.AdminOrderInterceptRuleRecord;
import com.zhyf.order.application.AdminOrderReceiptItem;
import com.zhyf.order.application.AdminOrderReceiptPage;
import com.zhyf.order.application.AdminOrderReceiptQuery;
import com.zhyf.order.application.AdminOrderRecheckItem;
import com.zhyf.order.application.AdminOrderRecheckPage;
import com.zhyf.order.application.AdminOrderRecheckQuery;
import com.zhyf.order.application.AdminOrderReviewItem;
import com.zhyf.order.application.AdminOrderReviewPage;
import com.zhyf.order.application.AdminOrderReviewQuery;
import com.zhyf.order.application.AdminOrderWarehouseItem;
import com.zhyf.order.application.AdminOrderWarehousePage;
import com.zhyf.order.application.AdminOrderWarehouseQuery;
import com.zhyf.order.application.AdminOperatorPage;
import com.zhyf.order.application.AdminOperatorQuery;
import com.zhyf.order.application.AdminOperatorRecord;
import com.zhyf.order.application.AdminOperatorRolePage;
import com.zhyf.order.application.AdminOperatorRoleQuery;
import com.zhyf.order.application.AdminOperatorRoleRecord;
import com.zhyf.order.application.AdminOrderDetail;
import com.zhyf.order.application.AdminOrderSearchQuery;
import com.zhyf.order.application.AdminPrescriptionReprintItem;
import com.zhyf.order.application.AdminPrescriptionReprintPage;
import com.zhyf.order.application.AdminPrescriptionReprintQuery;
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

    public Optional<OrderSnapshot> findOrderByLegacyPdaRecipeId(String recipeId) {
        String sql = """
                select distinct o.id, o.tenant_id, o.institution_id, o.order_no, o.external_order_no, o.status, o.created_at
                from order_main o
                left join prescription p on p.order_id = o.id
                where o.order_no = ?
                   or o.external_order_no = ?
                   or p.prescription_no = ?
                   or p.external_prescription_no = ?
                order by o.created_at desc
                limit 1
                """;
        return jdbcTemplate.query(sql, this::mapOrderSnapshot, recipeId, recipeId, recipeId, recipeId)
                .stream()
                .findFirst();
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
                    coalesce(o.logistics_fee, order_amount.logistics_fee) as logistics_fee,
                    coalesce(o.discount_amount, order_amount.discount_amount) as discount_amount,
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
                left join lateral (
                    select
                        (
                            select candidate.amount_text::numeric
                            from (
                                select nullif(trim(payload_field.value), '') as amount_text
                                from jsonb_each_text(o.raw_payload) payload_field
                                where payload_field.key in (
                                    'logisticsFee', 'logistics_fee', 'freight', 'freightFee',
                                    'freight_fee', 'shippingFee', 'shipping_fee', 'deliveryFee',
                                    'delivery_fee', 'expressFee', 'express_fee'
                                )
                                order by array_position(array[
                                    'logisticsFee', 'logistics_fee', 'freight', 'freightFee',
                                    'freight_fee', 'shippingFee', 'shipping_fee', 'deliveryFee',
                                    'delivery_fee', 'expressFee', 'express_fee'
                                ], payload_field.key)
                                limit 1
                            ) candidate
                            where candidate.amount_text ~ '^-?[0-9]+([.][0-9]+)?$'
                        ) as logistics_fee,
                        (
                            select candidate.amount_text::numeric
                            from (
                                select nullif(trim(payload_field.value), '') as amount_text
                                from jsonb_each_text(o.raw_payload) payload_field
                                where payload_field.key in (
                                    'discountAmount', 'discount_amount', 'discount', 'couponAmount',
                                    'coupon_amount', 'preferentialAmount', 'preferential_amount',
                                    'reduceAmount', 'reduce_amount', 'promotionAmount', 'promotion_amount'
                                )
                                order by array_position(array[
                                    'discountAmount', 'discount_amount', 'discount', 'couponAmount',
                                    'coupon_amount', 'preferentialAmount', 'preferential_amount',
                                    'reduceAmount', 'reduce_amount', 'promotionAmount', 'promotion_amount'
                                ], payload_field.key)
                                limit 1
                            ) candidate
                            where candidate.amount_text ~ '^-?[0-9]+([.][0-9]+)?$'
                        ) as discount_amount
                ) order_amount on true
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
                        header.logisticsFee(),
                        header.discountAmount(),
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
                    p.boil_times,
                    p.is_within,
                    p.per_pack_num,
                    p.per_pack_dose,
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
                         p.boil_times, p.is_within, p.per_pack_num, p.per_pack_dose,
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
                        prescription.boilTimes(),
                        prescription.isWithin(),
                        prescription.perPackNum(),
                        prescription.perPackDose(),
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

    public AdminOperatorPage searchAdminOperators(AdminOperatorQuery query) {
        QueryParts filters = adminOperatorFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from operator_user u
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select id, tenant_id, username, display_name, role_code, enabled, created_at, updated_at
                from operator_user u
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by enabled desc, username asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminOperatorPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminOperatorRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public AdminOperatorRolePage searchAdminOperatorRoles(AdminOperatorRoleQuery query) {
        QueryParts filters = adminOperatorRoleFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from (
                    select u.role_code
                    from operator_user u
                    where u.role_code is not null and u.role_code <> ''
                """);
        countQuery.append(filters.sql());
        countQuery.append(" group by u.role_code) roles");
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select u.role_code,
                       count(*) as operator_count,
                       count(*) filter (where u.enabled) as enabled_count,
                       count(*) filter (where not u.enabled) as disabled_count,
                       min(u.created_at) as created_at,
                       max(u.updated_at) as updated_at
                from operator_user u
                where u.role_code is not null and u.role_code <> ''
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" group by u.role_code order by u.role_code asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminOperatorRolePage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminOperatorRoleRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminOperatorRoleRecord> findAdminOperatorRole(String roleCode) {
        String sql = """
                select u.role_code,
                       count(*) as operator_count,
                       count(*) filter (where u.enabled) as enabled_count,
                       count(*) filter (where not u.enabled) as disabled_count,
                       min(u.created_at) as created_at,
                       max(u.updated_at) as updated_at
                from operator_user u
                where u.role_code = ?
                group by u.role_code
                """;
        return jdbcTemplate.query(sql, this::mapAdminOperatorRoleRecord, roleCode).stream().findFirst();
    }

    public int renameAdminOperatorRole(String oldRoleCode, String newRoleCode) {
        String sql = """
                update operator_user
                set role_code = ?,
                    updated_at = now()
                where role_code = ?
                """;
        return jdbcTemplate.update(sql, newRoleCode, oldRoleCode);
    }

    public Optional<AdminOperatorRecord> findAdminOperatorById(UUID id) {
        String sql = """
                select id, tenant_id, username, display_name, role_code, enabled, created_at, updated_at
                from operator_user
                where id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminOperatorRecord, id).stream().findFirst();
    }

    public Optional<AdminOperatorRecord> findAdminOperatorByUsername(UUID tenantId, String username) {
        String sql = """
                select id, tenant_id, username, display_name, role_code, enabled, created_at, updated_at
                from operator_user
                where tenant_id = ? and username = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminOperatorRecord, tenantId, username).stream().findFirst();
    }

    public AdminOperatorRecord insertAdminOperator(
            UUID id,
            UUID tenantId,
            String username,
            String displayName,
            String roleCode,
            boolean enabled
    ) {
        String sql = """
                insert into operator_user (id, tenant_id, username, display_name, role_code, enabled)
                values (?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, username, displayName, roleCode, enabled);
        return findAdminOperatorById(id).orElseThrow();
    }

    public AdminOperatorRecord updateAdminOperator(
            UUID id,
            String displayName,
            String roleCode,
            boolean enabled
    ) {
        String sql = """
                update operator_user
                set display_name = ?,
                    role_code = ?,
                    enabled = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, displayName, roleCode, enabled, id);
        return findAdminOperatorById(id).orElseThrow();
    }

    public AdminDictTypePage searchAdminDictTypes(AdminDictTypeQuery query) {
        QueryParts filters = adminDictTypeFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from dict_type t
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select id, tenant_id, type_code, type_name, enabled, created_at, updated_at
                from dict_type t
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by enabled desc, type_code asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminDictTypePage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminDictTypeRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminDictTypeRecord> findAdminDictTypeById(UUID id) {
        String sql = """
                select id, tenant_id, type_code, type_name, enabled, created_at, updated_at
                from dict_type
                where id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminDictTypeRecord, id).stream().findFirst();
    }

    public Optional<AdminDictTypeRecord> findAdminDictTypeByCode(UUID tenantId, String typeCode) {
        String sql = """
                select id, tenant_id, type_code, type_name, enabled, created_at, updated_at
                from dict_type
                where tenant_id = ? and type_code = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminDictTypeRecord, tenantId, typeCode).stream().findFirst();
    }

    public AdminDictTypeRecord insertAdminDictType(
            UUID id,
            UUID tenantId,
            String typeCode,
            String typeName,
            boolean enabled
    ) {
        String sql = """
                insert into dict_type (id, tenant_id, type_code, type_name, enabled)
                values (?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, typeCode, typeName, enabled);
        return findAdminDictTypeById(id).orElseThrow();
    }

    public AdminDictTypeRecord updateAdminDictType(
            UUID id,
            String typeName,
            boolean enabled
    ) {
        String sql = """
                update dict_type
                set type_name = ?,
                    enabled = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, typeName, enabled, id);
        return findAdminDictTypeById(id).orElseThrow();
    }

    public AdminDictItemPage searchAdminDictItems(AdminDictItemQuery query) {
        QueryParts filters = adminDictItemFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from dict_item i
                join dict_type t on t.id = i.type_id
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select i.id, i.tenant_id, i.type_id, t.type_code, t.type_name,
                       i.item_code, i.item_name, i.item_value, i.sort_no, i.enabled,
                       i.remark, i.created_at, i.updated_at
                from dict_item i
                join dict_type t on t.id = i.type_id
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by t.type_code asc, i.sort_no asc, i.item_code asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminDictItemPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminDictItemRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminDictItemRecord> findAdminDictItemById(UUID id) {
        String sql = """
                select i.id, i.tenant_id, i.type_id, t.type_code, t.type_name,
                       i.item_code, i.item_name, i.item_value, i.sort_no, i.enabled,
                       i.remark, i.created_at, i.updated_at
                from dict_item i
                join dict_type t on t.id = i.type_id
                where i.id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminDictItemRecord, id).stream().findFirst();
    }

    public Optional<AdminDictItemRecord> findAdminDictItemByCode(UUID typeId, String itemCode) {
        String sql = """
                select i.id, i.tenant_id, i.type_id, t.type_code, t.type_name,
                       i.item_code, i.item_name, i.item_value, i.sort_no, i.enabled,
                       i.remark, i.created_at, i.updated_at
                from dict_item i
                join dict_type t on t.id = i.type_id
                where i.type_id = ? and i.item_code = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminDictItemRecord, typeId, itemCode).stream().findFirst();
    }

    public AdminDictItemRecord insertAdminDictItem(
            UUID id,
            UUID tenantId,
            UUID typeId,
            String itemCode,
            String itemName,
            String itemValue,
            int sortNo,
            boolean enabled,
            String remark
    ) {
        String sql = """
                insert into dict_item (
                    id, tenant_id, type_id, item_code, item_name, item_value, sort_no, enabled, remark
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, typeId, itemCode, itemName, itemValue, sortNo, enabled, remark);
        return findAdminDictItemById(id).orElseThrow();
    }

    public AdminDictItemRecord updateAdminDictItem(
            UUID id,
            String itemName,
            String itemValue,
            int sortNo,
            boolean enabled,
            String remark
    ) {
        String sql = """
                update dict_item
                set item_name = ?,
                    item_value = ?,
                    sort_no = ?,
                    enabled = ?,
                    remark = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, itemName, itemValue, sortNo, enabled, remark, id);
        return findAdminDictItemById(id).orElseThrow();
    }

    public AdminSystemConfigPage searchAdminSystemConfigs(AdminSystemConfigQuery query) {
        QueryParts filters = adminSystemConfigFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from system_config c
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select id, tenant_id, config_key, config_name, config_value, value_type,
                       enabled, remark, created_at, updated_at
                from system_config c
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by enabled desc, config_key asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminSystemConfigPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminSystemConfigRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminSystemConfigRecord> findAdminSystemConfigById(UUID id) {
        String sql = """
                select id, tenant_id, config_key, config_name, config_value, value_type,
                       enabled, remark, created_at, updated_at
                from system_config
                where id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminSystemConfigRecord, id).stream().findFirst();
    }

    public Optional<AdminSystemConfigRecord> findAdminSystemConfigByKey(UUID tenantId, String configKey) {
        String sql = """
                select id, tenant_id, config_key, config_name, config_value, value_type,
                       enabled, remark, created_at, updated_at
                from system_config
                where tenant_id = ? and config_key = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminSystemConfigRecord, tenantId, configKey).stream().findFirst();
    }

    public AdminSystemConfigRecord insertAdminSystemConfig(
            UUID id,
            UUID tenantId,
            String configKey,
            String configName,
            String configValue,
            String valueType,
            boolean enabled,
            String remark
    ) {
        String sql = """
                insert into system_config (
                    id, tenant_id, config_key, config_name, config_value, value_type, enabled, remark
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, configKey, configName, configValue, valueType, enabled, remark);
        return findAdminSystemConfigById(id).orElseThrow();
    }

    public AdminSystemConfigRecord updateAdminSystemConfig(
            UUID id,
            String configName,
            String configValue,
            String valueType,
            boolean enabled,
            String remark
    ) {
        String sql = """
                update system_config
                set config_name = ?,
                    config_value = ?,
                    value_type = ?,
                    enabled = ?,
                    remark = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, configName, configValue, valueType, enabled, remark, id);
        return findAdminSystemConfigById(id).orElseThrow();
    }

    public AdminDecoctCenterPage searchAdminDecoctCenters(AdminDecoctCenterQuery query) {
        QueryParts filters = adminDecoctCenterFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from decoct_center c
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select id, tenant_id, center_code, center_name, contact_name, contact_phone,
                       address, enabled, remark, created_at, updated_at
                from decoct_center c
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by enabled desc, center_code asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminDecoctCenterPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminDecoctCenterRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminDecoctCenterRecord> findAdminDecoctCenterById(UUID id) {
        String sql = """
                select id, tenant_id, center_code, center_name, contact_name, contact_phone,
                       address, enabled, remark, created_at, updated_at
                from decoct_center
                where id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminDecoctCenterRecord, id).stream().findFirst();
    }

    public Optional<AdminDecoctCenterRecord> findAdminDecoctCenterByCode(UUID tenantId, String centerCode) {
        String sql = """
                select id, tenant_id, center_code, center_name, contact_name, contact_phone,
                       address, enabled, remark, created_at, updated_at
                from decoct_center
                where tenant_id = ? and center_code = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminDecoctCenterRecord, tenantId, centerCode).stream().findFirst();
    }

    public AdminDecoctCenterRecord insertAdminDecoctCenter(
            UUID id,
            UUID tenantId,
            String centerCode,
            String centerName,
            String contactName,
            String contactPhone,
            String address,
            boolean enabled,
            String remark
    ) {
        String sql = """
                insert into decoct_center (
                    id, tenant_id, center_code, center_name, contact_name, contact_phone, address, enabled, remark
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, centerCode, centerName, contactName, contactPhone, address, enabled, remark);
        return findAdminDecoctCenterById(id).orElseThrow();
    }

    public AdminDecoctCenterRecord updateAdminDecoctCenter(
            UUID id,
            String centerName,
            String contactName,
            String contactPhone,
            String address,
            boolean enabled,
            String remark
    ) {
        String sql = """
                update decoct_center
                set center_name = ?,
                    contact_name = ?,
                    contact_phone = ?,
                    address = ?,
                    enabled = ?,
                    remark = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, centerName, contactName, contactPhone, address, enabled, remark, id);
        return findAdminDecoctCenterById(id).orElseThrow();
    }

    public AdminHerbPage searchAdminHerbs(AdminHerbQuery query) {
        QueryParts filters = adminHerbFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from herb_catalog h
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select id, tenant_id, herb_code, herb_name, drug_specs, drug_origin,
                       unit, retail_price, enabled, remark, created_at, updated_at
                from herb_catalog h
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by enabled desc, herb_code asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminHerbPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminHerbRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminHerbRecord> findAdminHerbById(UUID id) {
        String sql = """
                select id, tenant_id, herb_code, herb_name, drug_specs, drug_origin,
                       unit, retail_price, enabled, remark, created_at, updated_at
                from herb_catalog
                where id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminHerbRecord, id).stream().findFirst();
    }

    public Optional<AdminHerbRecord> findAdminHerbByCode(UUID tenantId, String herbCode) {
        String sql = """
                select id, tenant_id, herb_code, herb_name, drug_specs, drug_origin,
                       unit, retail_price, enabled, remark, created_at, updated_at
                from herb_catalog
                where tenant_id = ? and herb_code = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminHerbRecord, tenantId, herbCode).stream().findFirst();
    }

    public AdminHerbRecord insertAdminHerb(
            UUID id,
            UUID tenantId,
            String herbCode,
            String herbName,
            String drugSpecs,
            String drugOrigin,
            String unit,
            BigDecimal retailPrice,
            boolean enabled,
            String remark
    ) {
        String sql = """
                insert into herb_catalog (
                    id, tenant_id, herb_code, herb_name, drug_specs, drug_origin,
                    unit, retail_price, enabled, remark
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, herbCode, herbName, drugSpecs, drugOrigin, unit, retailPrice, enabled, remark);
        return findAdminHerbById(id).orElseThrow();
    }

    public AdminHerbRecord updateAdminHerb(
            UUID id,
            String herbName,
            String drugSpecs,
            String drugOrigin,
            String unit,
            BigDecimal retailPrice,
            boolean enabled,
            String remark
    ) {
        String sql = """
                update herb_catalog
                set herb_name = ?,
                    drug_specs = ?,
                    drug_origin = ?,
                    unit = ?,
                    retail_price = ?,
                    enabled = ?,
                    remark = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, herbName, drugSpecs, drugOrigin, unit, retailPrice, enabled, remark, id);
        return findAdminHerbById(id).orElseThrow();
    }

    public AdminHerbAreaPage searchAdminHerbAreas(AdminHerbAreaQuery query) {
        QueryParts filters = adminHerbAreaFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from herb_area a
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select id, tenant_id, area_code, area_name, enabled, remark, created_at, updated_at
                from herb_area a
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by enabled desc, area_code asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminHerbAreaPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminHerbAreaRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminHerbAreaRecord> findAdminHerbAreaById(UUID id) {
        String sql = """
                select id, tenant_id, area_code, area_name, enabled, remark, created_at, updated_at
                from herb_area
                where id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminHerbAreaRecord, id).stream().findFirst();
    }

    public Optional<AdminHerbAreaRecord> findAdminHerbAreaByCode(UUID tenantId, String areaCode) {
        String sql = """
                select id, tenant_id, area_code, area_name, enabled, remark, created_at, updated_at
                from herb_area
                where tenant_id = ? and area_code = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminHerbAreaRecord, tenantId, areaCode).stream().findFirst();
    }

    public AdminHerbAreaRecord insertAdminHerbArea(
            UUID id,
            UUID tenantId,
            String areaCode,
            String areaName,
            boolean enabled,
            String remark
    ) {
        String sql = """
                insert into herb_area (
                    id, tenant_id, area_code, area_name, enabled, remark
                )
                values (?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, areaCode, areaName, enabled, remark);
        return findAdminHerbAreaById(id).orElseThrow();
    }

    public AdminHerbAreaRecord updateAdminHerbArea(
            UUID id,
            String areaName,
            boolean enabled,
            String remark
    ) {
        String sql = """
                update herb_area
                set area_name = ?,
                    enabled = ?,
                    remark = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, areaName, enabled, remark, id);
        return findAdminHerbAreaById(id).orElseThrow();
    }

    public AdminHerbIndexPage searchAdminHerbIndexes(AdminHerbIndexQuery query) {
        QueryParts filters = adminHerbIndexFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from herb_index x
                join institution i on i.id = x.institution_id
                join herb_catalog h on h.id = x.herb_id
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select x.id, x.tenant_id, x.institution_id, i.institution_code, i.institution_name,
                       x.external_herb_code, x.external_herb_name, x.herb_id, h.herb_code, h.herb_name,
                       x.match_type, x.enabled, x.remark, x.created_at, x.updated_at
                from herb_index x
                join institution i on i.id = x.institution_id
                join herb_catalog h on h.id = x.herb_id
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by x.enabled desc, i.institution_name asc, x.external_herb_code asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminHerbIndexPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminHerbIndexRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminHerbIndexRecord> findAdminHerbIndexById(UUID id) {
        String sql = """
                select x.id, x.tenant_id, x.institution_id, i.institution_code, i.institution_name,
                       x.external_herb_code, x.external_herb_name, x.herb_id, h.herb_code, h.herb_name,
                       x.match_type, x.enabled, x.remark, x.created_at, x.updated_at
                from herb_index x
                join institution i on i.id = x.institution_id
                join herb_catalog h on h.id = x.herb_id
                where x.id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminHerbIndexRecord, id).stream().findFirst();
    }

    public Optional<AdminHerbIndexRecord> findAdminHerbIndexByExternalCode(
            UUID tenantId,
            UUID institutionId,
            String externalHerbCode
    ) {
        String sql = """
                select x.id, x.tenant_id, x.institution_id, i.institution_code, i.institution_name,
                       x.external_herb_code, x.external_herb_name, x.herb_id, h.herb_code, h.herb_name,
                       x.match_type, x.enabled, x.remark, x.created_at, x.updated_at
                from herb_index x
                join institution i on i.id = x.institution_id
                join herb_catalog h on h.id = x.herb_id
                where x.tenant_id = ? and x.institution_id = ? and x.external_herb_code = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminHerbIndexRecord, tenantId, institutionId, externalHerbCode)
                .stream()
                .findFirst();
    }

    public AdminHerbIndexRecord insertAdminHerbIndex(
            UUID id,
            UUID tenantId,
            UUID institutionId,
            String externalHerbCode,
            String externalHerbName,
            UUID herbId,
            String matchType,
            boolean enabled,
            String remark
    ) {
        String sql = """
                insert into herb_index (
                    id, tenant_id, institution_id, external_herb_code, external_herb_name,
                    herb_id, match_type, enabled, remark
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                id,
                tenantId,
                institutionId,
                externalHerbCode,
                externalHerbName,
                herbId,
                matchType,
                enabled,
                remark
        );
        return findAdminHerbIndexById(id).orElseThrow();
    }

    public AdminHerbIndexRecord updateAdminHerbIndex(
            UUID id,
            String externalHerbName,
            UUID herbId,
            String matchType,
            boolean enabled,
            String remark
    ) {
        String sql = """
                update herb_index
                set external_herb_name = ?,
                    herb_id = ?,
                    match_type = ?,
                    enabled = ?,
                    remark = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, externalHerbName, herbId, matchType, enabled, remark, id);
        return findAdminHerbIndexById(id).orElseThrow();
    }

    public AdminHerbIndexOperationLogPage searchAdminHerbIndexOperationLogs(
            AdminHerbIndexOperationLogQuery query
    ) {
        QueryParts filters = adminHerbIndexOperationLogFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from herb_index_operation_log l
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select id, tenant_id, index_id, institution_id, institution_code, institution_name,
                       external_herb_code, external_herb_name, herb_id, herb_code, herb_name,
                       action_type, operator, remark, created_at
                from herb_index_operation_log l
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by created_at desc, id desc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminHerbIndexOperationLogPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminHerbIndexOperationLogRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public void insertAdminHerbIndexOperationLog(
            UUID id,
            UUID tenantId,
            UUID indexId,
            UUID institutionId,
            String institutionCode,
            String institutionName,
            String externalHerbCode,
            String externalHerbName,
            UUID herbId,
            String herbCode,
            String herbName,
            String actionType,
            String operator,
            String remark
    ) {
        String sql = """
                insert into herb_index_operation_log (
                    id, tenant_id, index_id, institution_id, institution_code, institution_name,
                    external_herb_code, external_herb_name, herb_id, herb_code, herb_name,
                    action_type, operator, remark
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                id,
                tenantId,
                indexId,
                institutionId,
                institutionCode,
                institutionName,
                externalHerbCode,
                externalHerbName,
                herbId,
                herbCode,
                herbName,
                actionType,
                operator,
                remark
        );
    }

    public AdminInstitutionPage searchAdminInstitutions(AdminInstitutionQuery query) {
        QueryParts filters = adminInstitutionFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from institution i
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select id, tenant_id, institution_code, institution_name, institution_type, status,
                       storage_type, created_at, updated_at
                from institution i
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by status asc, institution_name asc, institution_code asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminInstitutionPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminInstitutionRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminInstitutionRecord> findAdminInstitutionById(UUID id) {
        String sql = """
                select id, tenant_id, institution_code, institution_name, institution_type, status,
                       storage_type, created_at, updated_at
                from institution
                where id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminInstitutionRecord, id).stream().findFirst();
    }

    public Optional<AdminInstitutionRecord> findAdminInstitutionByCode(UUID tenantId, String institutionCode) {
        String sql = """
                select id, tenant_id, institution_code, institution_name, institution_type, status,
                       storage_type, created_at, updated_at
                from institution
                where tenant_id = ? and institution_code = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminInstitutionRecord, tenantId, institutionCode).stream().findFirst();
    }

    public AdminInstitutionRecord insertAdminInstitution(
            UUID id,
            UUID tenantId,
            String institutionCode,
            String institutionName,
            String institutionType,
            String status,
            String storageType
    ) {
        String sql = """
                insert into institution (
                    id, tenant_id, institution_code, institution_name, institution_type, status, storage_type
                )
                values (?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, institutionCode, institutionName, institutionType, status, storageType);
        return findAdminInstitutionById(id).orElseThrow();
    }

    public AdminInstitutionRecord updateAdminInstitution(
            UUID id,
            String institutionName,
            String institutionType,
            String status,
            String storageType
    ) {
        String sql = """
                update institution
                set institution_name = ?,
                    institution_type = ?,
                    status = ?,
                    storage_type = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, institutionName, institutionType, status, storageType, id);
        return findAdminInstitutionById(id).orElseThrow();
    }

    public AdminInstitutionAppPage searchAdminInstitutionApps(AdminInstitutionAppQuery query) {
        QueryParts filters = adminInstitutionAppFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from institution_app a
                join institution i on i.id = a.institution_id
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select a.id, a.tenant_id, a.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, a.app_key, a.sign_type, a.callback_url, a.enabled,
                       (a.app_secret is not null and a.app_secret <> '') as app_secret_configured,
                       a.created_at, a.updated_at
                from institution_app a
                join institution i on i.id = a.institution_id
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by a.enabled desc, i.institution_name asc, a.app_key asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminInstitutionAppPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminInstitutionAppRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminInstitutionAppRecord> findAdminInstitutionAppById(UUID id) {
        String sql = """
                select a.id, a.tenant_id, a.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, a.app_key, a.sign_type, a.callback_url, a.enabled,
                       (a.app_secret is not null and a.app_secret <> '') as app_secret_configured,
                       a.created_at, a.updated_at
                from institution_app a
                join institution i on i.id = a.institution_id
                where a.id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminInstitutionAppRecord, id).stream().findFirst();
    }

    public Optional<AdminInstitutionAppRecord> findAdminInstitutionAppByAppKey(String appKey) {
        String sql = """
                select a.id, a.tenant_id, a.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, a.app_key, a.sign_type, a.callback_url, a.enabled,
                       (a.app_secret is not null and a.app_secret <> '') as app_secret_configured,
                       a.created_at, a.updated_at
                from institution_app a
                join institution i on i.id = a.institution_id
                where a.app_key = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminInstitutionAppRecord, appKey).stream().findFirst();
    }

    public AdminInstitutionAppRecord insertAdminInstitutionApp(
            UUID id,
            UUID tenantId,
            UUID institutionId,
            String appKey,
            String appSecret,
            String signType,
            String callbackUrl,
            boolean enabled
    ) {
        String sql = """
                insert into institution_app (
                    id, tenant_id, institution_id, app_key, app_secret, sign_type, callback_url, enabled
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, institutionId, appKey, appSecret, signType, callbackUrl, enabled);
        return findAdminInstitutionAppById(id).orElseThrow();
    }

    public AdminInstitutionAppRecord updateAdminInstitutionApp(
            UUID id,
            String appSecret,
            String signType,
            String callbackUrl,
            boolean enabled
    ) {
        String sql = """
                update institution_app
                set app_secret = coalesce(?, app_secret),
                    sign_type = ?,
                    callback_url = ?,
                    enabled = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, appSecret, signType, callbackUrl, enabled, id);
        return findAdminInstitutionAppById(id).orElseThrow();
    }

    public AdminInstitutionApiPage searchAdminInstitutionApis(AdminInstitutionApiQuery query) {
        QueryParts filters = adminInstitutionApiFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from institution_api_definition a
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select id, api_code, api_name, request_method, request_path, description,
                       enabled, created_at, updated_at
                from institution_api_definition a
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by enabled desc, api_code asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminInstitutionApiPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminInstitutionApiRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminInstitutionApiRecord> findAdminInstitutionApiById(UUID id) {
        String sql = """
                select id, api_code, api_name, request_method, request_path, description,
                       enabled, created_at, updated_at
                from institution_api_definition
                where id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminInstitutionApiRecord, id).stream().findFirst();
    }

    public Optional<AdminInstitutionApiRecord> findAdminInstitutionApiByCode(String apiCode) {
        String sql = """
                select id, api_code, api_name, request_method, request_path, description,
                       enabled, created_at, updated_at
                from institution_api_definition
                where api_code = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminInstitutionApiRecord, apiCode).stream().findFirst();
    }

    public AdminInstitutionApiRecord insertAdminInstitutionApi(
            UUID id,
            String apiCode,
            String apiName,
            String requestMethod,
            String requestPath,
            String description,
            boolean enabled
    ) {
        String sql = """
                insert into institution_api_definition (
                    id, api_code, api_name, request_method, request_path, description, enabled
                )
                values (?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, apiCode, apiName, requestMethod, requestPath, description, enabled);
        return findAdminInstitutionApiById(id).orElseThrow();
    }

    public AdminInstitutionApiRecord updateAdminInstitutionApi(
            UUID id,
            String apiName,
            String requestMethod,
            String requestPath,
            String description,
            boolean enabled
    ) {
        String sql = """
                update institution_api_definition
                set api_name = ?,
                    request_method = ?,
                    request_path = ?,
                    description = ?,
                    enabled = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, apiName, requestMethod, requestPath, description, enabled, id);
        return findAdminInstitutionApiById(id).orElseThrow();
    }

    public AdminInstitutionApiPermissionPage searchAdminInstitutionApiPermissions(
            AdminInstitutionApiPermissionQuery query
    ) {
        QueryParts filters = adminInstitutionApiPermissionFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from institution_api_permission p
                join institution i on i.id = p.institution_id
                join institution_api_definition a on a.id = p.api_id
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select p.id, p.tenant_id, p.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, p.api_id, a.api_code, a.api_name, a.request_method,
                       a.request_path, p.enabled, p.remark, p.created_at, p.updated_at
                from institution_api_permission p
                join institution i on i.id = p.institution_id
                join institution_api_definition a on a.id = p.api_id
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by p.enabled desc, i.institution_name asc, a.api_code asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminInstitutionApiPermissionPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminInstitutionApiPermissionRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminInstitutionApiPermissionRecord> findAdminInstitutionApiPermissionById(UUID id) {
        String sql = """
                select p.id, p.tenant_id, p.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, p.api_id, a.api_code, a.api_name, a.request_method,
                       a.request_path, p.enabled, p.remark, p.created_at, p.updated_at
                from institution_api_permission p
                join institution i on i.id = p.institution_id
                join institution_api_definition a on a.id = p.api_id
                where p.id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminInstitutionApiPermissionRecord, id).stream().findFirst();
    }

    public Optional<AdminInstitutionApiPermissionRecord> findAdminInstitutionApiPermissionByInstitutionAndApi(
            UUID institutionId,
            UUID apiId
    ) {
        String sql = """
                select p.id, p.tenant_id, p.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, p.api_id, a.api_code, a.api_name, a.request_method,
                       a.request_path, p.enabled, p.remark, p.created_at, p.updated_at
                from institution_api_permission p
                join institution i on i.id = p.institution_id
                join institution_api_definition a on a.id = p.api_id
                where p.institution_id = ? and p.api_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminInstitutionApiPermissionRecord, institutionId, apiId)
                .stream()
                .findFirst();
    }

    public AdminInstitutionApiPermissionRecord insertAdminInstitutionApiPermission(
            UUID id,
            UUID tenantId,
            UUID institutionId,
            UUID apiId,
            String remark,
            boolean enabled
    ) {
        String sql = """
                insert into institution_api_permission (
                    id, tenant_id, institution_id, api_id, remark, enabled
                )
                values (?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, institutionId, apiId, remark, enabled);
        return findAdminInstitutionApiPermissionById(id).orElseThrow();
    }

    public AdminInstitutionApiPermissionRecord updateAdminInstitutionApiPermission(
            UUID id,
            String remark,
            boolean enabled
    ) {
        String sql = """
                update institution_api_permission
                set remark = ?,
                    enabled = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, remark, enabled, id);
        return findAdminInstitutionApiPermissionById(id).orElseThrow();
    }

    public AdminInstitutionIpWhitelistPage searchAdminInstitutionIpWhitelists(
            AdminInstitutionIpWhitelistQuery query
    ) {
        QueryParts filters = adminInstitutionIpWhitelistFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from institution_ip_whitelist w
                join institution i on i.id = w.institution_id
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select w.id, w.tenant_id, w.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, w.ip_range, w.enabled, w.created_at
                from institution_ip_whitelist w
                join institution i on i.id = w.institution_id
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by w.enabled desc, i.institution_name asc, w.ip_range asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminInstitutionIpWhitelistPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminInstitutionIpWhitelistRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminInstitutionIpWhitelistRecord> findAdminInstitutionIpWhitelistById(UUID id) {
        String sql = """
                select w.id, w.tenant_id, w.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, w.ip_range, w.enabled, w.created_at
                from institution_ip_whitelist w
                join institution i on i.id = w.institution_id
                where w.id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminInstitutionIpWhitelistRecord, id).stream().findFirst();
    }

    public Optional<AdminInstitutionIpWhitelistRecord> findAdminInstitutionIpWhitelistByInstitutionAndRange(
            UUID institutionId,
            String ipRange
    ) {
        String sql = """
                select w.id, w.tenant_id, w.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, w.ip_range, w.enabled, w.created_at
                from institution_ip_whitelist w
                join institution i on i.id = w.institution_id
                where w.institution_id = ? and w.ip_range = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminInstitutionIpWhitelistRecord, institutionId, ipRange)
                .stream()
                .findFirst();
    }

    public AdminInstitutionIpWhitelistRecord insertAdminInstitutionIpWhitelist(
            UUID id,
            UUID tenantId,
            UUID institutionId,
            String ipRange,
            boolean enabled
    ) {
        String sql = """
                insert into institution_ip_whitelist (id, tenant_id, institution_id, ip_range, enabled)
                values (?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, institutionId, ipRange, enabled);
        return findAdminInstitutionIpWhitelistById(id).orElseThrow();
    }

    public AdminInstitutionIpWhitelistRecord updateAdminInstitutionIpWhitelist(
            UUID id,
            String ipRange,
            boolean enabled
    ) {
        String sql = """
                update institution_ip_whitelist
                set ip_range = ?,
                    enabled = ?
                where id = ?
                """;
        jdbcTemplate.update(sql, ipRange, enabled, id);
        return findAdminInstitutionIpWhitelistById(id).orElseThrow();
    }

    public AdminLogisticsSpecialRulePage searchAdminLogisticsSpecialRules(AdminLogisticsSpecialRuleQuery query) {
        QueryParts filters = adminLogisticsSpecialRuleFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from logistics_special_rule r
                join institution i on i.id = r.institution_id
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select r.id, r.tenant_id, r.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, r.rule_name, r.logistics_company, r.base_fee, r.extra_fee,
                       r.free_threshold, r.remark, r.enabled, r.created_at, r.updated_at
                from logistics_special_rule r
                join institution i on i.id = r.institution_id
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by r.enabled desc, i.institution_name asc, r.rule_name asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminLogisticsSpecialRulePage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminLogisticsSpecialRuleRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminLogisticsSpecialRuleRecord> findAdminLogisticsSpecialRuleById(UUID id) {
        String sql = """
                select r.id, r.tenant_id, r.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, r.rule_name, r.logistics_company, r.base_fee, r.extra_fee,
                       r.free_threshold, r.remark, r.enabled, r.created_at, r.updated_at
                from logistics_special_rule r
                join institution i on i.id = r.institution_id
                where r.id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminLogisticsSpecialRuleRecord, id).stream().findFirst();
    }

    public Optional<AdminLogisticsSpecialRuleRecord> findAdminLogisticsSpecialRuleByBusinessKey(
            UUID tenantId,
            UUID institutionId,
            String ruleName,
            String logisticsCompany
    ) {
        String sql = """
                select r.id, r.tenant_id, r.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, r.rule_name, r.logistics_company, r.base_fee, r.extra_fee,
                       r.free_threshold, r.remark, r.enabled, r.created_at, r.updated_at
                from logistics_special_rule r
                join institution i on i.id = r.institution_id
                where r.tenant_id = ? and r.institution_id = ? and r.rule_name = ? and r.logistics_company = ?
                """;
        return jdbcTemplate.query(
                        sql,
                        this::mapAdminLogisticsSpecialRuleRecord,
                        tenantId,
                        institutionId,
                        ruleName,
                        logisticsCompany
                )
                .stream()
                .findFirst();
    }

    public AdminLogisticsSpecialRuleRecord insertAdminLogisticsSpecialRule(
            UUID id,
            UUID tenantId,
            UUID institutionId,
            String ruleName,
            String logisticsCompany,
            BigDecimal baseFee,
            BigDecimal extraFee,
            BigDecimal freeThreshold,
            String remark,
            boolean enabled
    ) {
        String sql = """
                insert into logistics_special_rule (
                    id, tenant_id, institution_id, rule_name, logistics_company, base_fee,
                    extra_fee, free_threshold, remark, enabled
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                id,
                tenantId,
                institutionId,
                ruleName,
                logisticsCompany,
                baseFee,
                extraFee,
                freeThreshold,
                remark,
                enabled
        );
        return findAdminLogisticsSpecialRuleById(id).orElseThrow();
    }

    public AdminLogisticsSpecialRuleRecord updateAdminLogisticsSpecialRule(
            UUID id,
            String ruleName,
            String logisticsCompany,
            BigDecimal baseFee,
            BigDecimal extraFee,
            BigDecimal freeThreshold,
            String remark,
            boolean enabled
    ) {
        String sql = """
                update logistics_special_rule
                set rule_name = ?,
                    logistics_company = ?,
                    base_fee = ?,
                    extra_fee = ?,
                    free_threshold = ?,
                    remark = ?,
                    enabled = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, ruleName, logisticsCompany, baseFee, extraFee, freeThreshold, remark, enabled, id);
        return findAdminLogisticsSpecialRuleById(id).orElseThrow();
    }

    public AdminLogisticsAddressCostPage searchAdminLogisticsAddressCosts(AdminLogisticsAddressCostQuery query) {
        QueryParts filters = adminLogisticsAddressCostFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from logistics_address_cost c
                join institution i on i.id = c.institution_id
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select c.id, c.tenant_id, c.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, c.logistics_company, c.province, c.city, c.district,
                       c.cost_amount, c.remark, c.enabled, c.created_at, c.updated_at
                from logistics_address_cost c
                join institution i on i.id = c.institution_id
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by c.enabled desc, i.institution_name asc, c.province asc, c.city asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminLogisticsAddressCostPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminLogisticsAddressCostRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminLogisticsAddressCostRecord> findAdminLogisticsAddressCostById(UUID id) {
        String sql = """
                select c.id, c.tenant_id, c.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, c.logistics_company, c.province, c.city, c.district,
                       c.cost_amount, c.remark, c.enabled, c.created_at, c.updated_at
                from logistics_address_cost c
                join institution i on i.id = c.institution_id
                where c.id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminLogisticsAddressCostRecord, id).stream().findFirst();
    }

    public Optional<AdminLogisticsAddressCostRecord> findAdminLogisticsAddressCostByBusinessKey(
            UUID tenantId,
            UUID institutionId,
            String logisticsCompany,
            String province,
            String city,
            String district
    ) {
        String sql = """
                select c.id, c.tenant_id, c.institution_id, i.institution_code, i.institution_name,
                       i.institution_type, c.logistics_company, c.province, c.city, c.district,
                       c.cost_amount, c.remark, c.enabled, c.created_at, c.updated_at
                from logistics_address_cost c
                join institution i on i.id = c.institution_id
                where c.tenant_id = ?
                  and c.institution_id = ?
                  and c.logistics_company = ?
                  and c.province = ?
                  and c.city = ?
                  and c.district = ?
                """;
        return jdbcTemplate.query(
                        sql,
                        this::mapAdminLogisticsAddressCostRecord,
                        tenantId,
                        institutionId,
                        logisticsCompany,
                        province,
                        city,
                        district
                )
                .stream()
                .findFirst();
    }

    public AdminLogisticsAddressCostRecord insertAdminLogisticsAddressCost(
            UUID id,
            UUID tenantId,
            UUID institutionId,
            String logisticsCompany,
            String province,
            String city,
            String district,
            BigDecimal costAmount,
            String remark,
            boolean enabled
    ) {
        String sql = """
                insert into logistics_address_cost (
                    id, tenant_id, institution_id, logistics_company, province, city, district,
                    cost_amount, remark, enabled
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                id,
                tenantId,
                institutionId,
                logisticsCompany,
                province,
                city,
                district,
                costAmount,
                remark,
                enabled
        );
        return findAdminLogisticsAddressCostById(id).orElseThrow();
    }

    public AdminLogisticsAddressCostRecord updateAdminLogisticsAddressCost(
            UUID id,
            String logisticsCompany,
            String province,
            String city,
            String district,
            BigDecimal costAmount,
            String remark,
            boolean enabled
    ) {
        String sql = """
                update logistics_address_cost
                set logistics_company = ?,
                    province = ?,
                    city = ?,
                    district = ?,
                    cost_amount = ?,
                    remark = ?,
                    enabled = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, logisticsCompany, province, city, district, costAmount, remark, enabled, id);
        return findAdminLogisticsAddressCostById(id).orElseThrow();
    }

    public AdminOrderMergePage searchAdminOrderMerges(AdminOrderMergeQuery query) {
        QueryParts filters = adminOrderMergeFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from order_merge m
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select m.id, m.tenant_id, m.merge_no, m.logistics_company, m.logistics_no, m.status, m.remark,
                       coalesce(items.order_count, 0) as order_count,
                       coalesce(items.order_nos, '') as order_nos,
                       coalesce(items.institution_names, '') as institution_names,
                       m.created_at, m.updated_at
                from order_merge m
                left join lateral (
                    select count(mi.id)::int as order_count,
                           string_agg(mi.order_no, ', ' order by mi.order_no) as order_nos,
                           string_agg(distinct i.institution_name, ', ' order by i.institution_name) as institution_names
                    from order_merge_item mi
                    join order_main o on o.id = mi.order_id
                    join institution i on i.id = o.institution_id
                    where mi.merge_id = m.id
                ) items on true
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by m.created_at desc, m.merge_no desc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminOrderMergePage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminOrderMergeRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminOrderMergeRecord> findAdminOrderMergeById(UUID id) {
        String sql = """
                select m.id, m.tenant_id, m.merge_no, m.logistics_company, m.logistics_no, m.status, m.remark,
                       coalesce(items.order_count, 0) as order_count,
                       coalesce(items.order_nos, '') as order_nos,
                       coalesce(items.institution_names, '') as institution_names,
                       m.created_at, m.updated_at
                from order_merge m
                left join lateral (
                    select count(mi.id)::int as order_count,
                           string_agg(mi.order_no, ', ' order by mi.order_no) as order_nos,
                           string_agg(distinct i.institution_name, ', ' order by i.institution_name) as institution_names
                    from order_merge_item mi
                    join order_main o on o.id = mi.order_id
                    join institution i on i.id = o.institution_id
                    where mi.merge_id = m.id
                ) items on true
                where m.id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminOrderMergeRecord, id).stream().findFirst();
    }

    public List<AdminOrderMergeCandidate> findAdminOrderMergeCandidates(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return List.of();
        }
        QueryParts query = new QueryParts("""
                select tenant_id, id as order_id, order_no
                from order_main o
                where 1 = 1
                """);
        query.addInFilter("o.order_no", orderNos);
        return jdbcTemplate.query(query.sql(), this::mapAdminOrderMergeCandidate, query.args());
    }

    public boolean existsActiveOrderMergeItem(UUID orderId) {
        String sql = """
                select count(*)
                from order_merge_item
                where order_id = ? and active = true
                """;
        Long totalValue = jdbcTemplate.queryForObject(sql, Long.class, orderId);
        return totalValue != null && totalValue > 0;
    }

    public AdminOrderMergeRecord insertAdminOrderMerge(
            UUID id,
            UUID tenantId,
            String mergeNo,
            String logisticsCompany,
            String logisticsNo,
            String remark
    ) {
        String sql = """
                insert into order_merge (
                    id, tenant_id, merge_no, logistics_company, logistics_no, status, remark
                )
                values (?, ?, ?, ?, ?, 'ACTIVE', ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, mergeNo, logisticsCompany, logisticsNo, remark);
        return findAdminOrderMergeById(id).orElseThrow();
    }

    public void insertAdminOrderMergeItem(
            UUID id,
            UUID tenantId,
            UUID mergeId,
            UUID orderId,
            String orderNo
    ) {
        String sql = """
                insert into order_merge_item (id, tenant_id, merge_id, order_id, order_no, active)
                values (?, ?, ?, ?, ?, true)
                """;
        jdbcTemplate.update(sql, id, tenantId, mergeId, orderId, orderNo);
    }

    public AdminOrderMergeRecord cancelAdminOrderMerge(UUID id, String remark) {
        String updateMergeSql = """
                update order_merge
                set status = 'CANCELLED',
                    remark = coalesce(?, remark),
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(updateMergeSql, remark, id);
        jdbcTemplate.update("update order_merge_item set active = false where merge_id = ?", id);
        return findAdminOrderMergeById(id).orElseThrow();
    }

    public AdminOrderInterceptRulePage searchAdminOrderInterceptRules(AdminOrderInterceptRuleQuery query) {
        QueryParts filters = adminOrderInterceptRuleFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from order_intercept_rule r
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select id, tenant_id, rule_code, rule_name, intercept_stage, match_field, match_type,
                       match_value, reason, priority, enabled, created_at, updated_at
                from order_intercept_rule r
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by enabled desc, priority asc, rule_code asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminOrderInterceptRulePage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminOrderInterceptRuleRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminOrderInterceptRuleRecord> findAdminOrderInterceptRuleById(UUID id) {
        String sql = """
                select id, tenant_id, rule_code, rule_name, intercept_stage, match_field, match_type,
                       match_value, reason, priority, enabled, created_at, updated_at
                from order_intercept_rule
                where id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminOrderInterceptRuleRecord, id).stream().findFirst();
    }

    public Optional<AdminOrderInterceptRuleRecord> findAdminOrderInterceptRuleByCode(
            UUID tenantId,
            String ruleCode
    ) {
        String sql = """
                select id, tenant_id, rule_code, rule_name, intercept_stage, match_field, match_type,
                       match_value, reason, priority, enabled, created_at, updated_at
                from order_intercept_rule
                where tenant_id = ? and rule_code = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminOrderInterceptRuleRecord, tenantId, ruleCode)
                .stream()
                .findFirst();
    }

    public AdminOrderInterceptRuleRecord insertAdminOrderInterceptRule(
            UUID id,
            UUID tenantId,
            String ruleCode,
            String ruleName,
            String interceptStage,
            String matchField,
            String matchType,
            String matchValue,
            String reason,
            int priority,
            boolean enabled
    ) {
        String sql = """
                insert into order_intercept_rule (
                    id, tenant_id, rule_code, rule_name, intercept_stage, match_field, match_type,
                    match_value, reason, priority, enabled
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                id,
                tenantId,
                ruleCode,
                ruleName,
                interceptStage,
                matchField,
                matchType,
                matchValue,
                reason,
                priority,
                enabled
        );
        return findAdminOrderInterceptRuleById(id).orElseThrow();
    }

    public AdminOrderInterceptRuleRecord updateAdminOrderInterceptRule(
            UUID id,
            String ruleName,
            String interceptStage,
            String matchField,
            String matchType,
            String matchValue,
            String reason,
            int priority,
            boolean enabled
    ) {
        String sql = """
                update order_intercept_rule
                set rule_name = ?,
                    intercept_stage = ?,
                    match_field = ?,
                    match_type = ?,
                    match_value = ?,
                    reason = ?,
                    priority = ?,
                    enabled = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(sql, ruleName, interceptStage, matchField, matchType, matchValue, reason, priority, enabled, id);
        return findAdminOrderInterceptRuleById(id).orElseThrow();
    }

    public AdminLabelTemplatePage searchAdminLabelTemplates(AdminLabelTemplateQuery query) {
        QueryParts filters = adminLabelTemplateFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from label_template t
                left join institution i on i.id = t.institution_id
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts("""
                select t.id, t.tenant_id, t.template_code, t.template_name, t.scope_type,
                       t.institution_id, i.name as institution_name, t.prescription_type,
                       t.label_width_mm, t.label_height_mm, t.content_template, t.enabled,
                       t.created_at, t.updated_at
                from label_template t
                left join institution i on i.id = t.institution_id
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by t.enabled desc, t.updated_at desc, t.template_code asc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());
        return new AdminLabelTemplatePage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminLabelTemplateRecord, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public Optional<AdminLabelTemplateRecord> findAdminLabelTemplateById(UUID id) {
        String sql = """
                select t.id, t.tenant_id, t.template_code, t.template_name, t.scope_type,
                       t.institution_id, i.name as institution_name, t.prescription_type,
                       t.label_width_mm, t.label_height_mm, t.content_template, t.enabled,
                       t.created_at, t.updated_at
                from label_template t
                left join institution i on i.id = t.institution_id
                where t.id = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminLabelTemplateRecord, id).stream().findFirst();
    }

    public Optional<AdminLabelTemplateRecord> findAdminLabelTemplateByCode(UUID tenantId, String templateCode) {
        String sql = """
                select t.id, t.tenant_id, t.template_code, t.template_name, t.scope_type,
                       t.institution_id, i.name as institution_name, t.prescription_type,
                       t.label_width_mm, t.label_height_mm, t.content_template, t.enabled,
                       t.created_at, t.updated_at
                from label_template t
                left join institution i on i.id = t.institution_id
                where t.tenant_id = ? and t.template_code = ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminLabelTemplateRecord, tenantId, templateCode)
                .stream()
                .findFirst();
    }

    public AdminLabelTemplateRecord insertAdminLabelTemplate(
            UUID id,
            UUID tenantId,
            String templateCode,
            String templateName,
            String scopeType,
            UUID institutionId,
            String prescriptionType,
            int labelWidthMm,
            int labelHeightMm,
            String contentTemplate,
            boolean enabled
    ) {
        String sql = """
                insert into label_template (
                    id, tenant_id, template_code, template_name, scope_type, institution_id,
                    prescription_type, label_width_mm, label_height_mm, content_template, enabled
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                id,
                tenantId,
                templateCode,
                templateName,
                scopeType,
                institutionId,
                prescriptionType,
                labelWidthMm,
                labelHeightMm,
                contentTemplate,
                enabled
        );
        return findAdminLabelTemplateById(id).orElseThrow();
    }

    public AdminLabelTemplateRecord updateAdminLabelTemplate(
            UUID id,
            String templateName,
            String scopeType,
            UUID institutionId,
            String prescriptionType,
            int labelWidthMm,
            int labelHeightMm,
            String contentTemplate,
            boolean enabled
    ) {
        String sql = """
                update label_template
                set template_name = ?,
                    scope_type = ?,
                    institution_id = ?,
                    prescription_type = ?,
                    label_width_mm = ?,
                    label_height_mm = ?,
                    content_template = ?,
                    enabled = ?,
                    updated_at = now()
                where id = ?
                """;
        jdbcTemplate.update(
                sql,
                templateName,
                scopeType,
                institutionId,
                prescriptionType,
                labelWidthMm,
                labelHeightMm,
                contentTemplate,
                enabled,
                id
        );
        return findAdminLabelTemplateById(id).orElseThrow();
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
                    p.id as prescription_id,
                    p.status as prescription_status,
                    p.prescription_no as prescription_nos,
                    p.external_prescription_no as external_prescription_nos,
                    coalesce(p.prescription_type, '') as prescription_types,
                    coalesce(p.hospital_type, '') as hospital_types,
                    1 as prescription_count,
                    coalesce(pd.detail_count, 0) as detail_count,
                    p.dose_count,
                    p.is_within,
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

    public AdminManualProcessPage searchAdminManualProcessOrders(AdminManualProcessQuery query) {
        QueryParts filters = adminManualProcessFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(distinct o.id)
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
                    o.receiver_name,
                    o.receiver_phone,
                    o.receiver_province,
                    o.receiver_city,
                    o.receiver_zone,
                    o.receiver_address,
                    o.address_type,
                    o.patient_name as patient_names,
                    string_agg(distinct nullif(p.hospital_type, ''), ',') as hospital_types,
                    string_agg(distinct nullif(p.prescription_type, ''), ',') as prescription_types,
                    string_agg(distinct nullif(p.prescription_no, ''), ',') as prescription_nos,
                    string_agg(distinct nullif(p.external_prescription_no, ''), ',') as external_prescription_nos,
                    string_agg(p.dose_count::text, ',' order by p.prescription_no)
                        filter (where p.dose_count is not null) as dose_counts,
                    count(distinct p.id)::int as prescription_count,
                    o.delivery_time,
                    o.order_remark,
                    o.created_at,
                    o.updated_at
                from order_main o
                join prescription p on p.order_id = o.id
                join institution i on i.id = o.institution_id
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append("""
                 group by o.id, o.tenant_id, o.institution_id, i.institution_name, i.storage_type,
                          o.order_no, o.external_order_no, o.status, o.patient_name, o.receiver_name, o.receiver_phone,
                          o.receiver_province, o.receiver_city, o.receiver_zone, o.receiver_address,
                          o.address_type, o.delivery_time, o.order_remark, o.created_at, o.updated_at
                 order by o.created_at desc, o.order_no desc limit ? offset ?
                """);
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());

        return new AdminManualProcessPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminManualProcessItem, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public AdminOrderRecheckPage searchAdminOrderRechecks(AdminOrderRecheckQuery query) {
        QueryParts filters = adminOrderRecheckFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(distinct p.id)
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
                    p.id as prescription_id,
                    i.institution_name,
                    i.storage_type,
                    o.order_no,
                    o.external_order_no,
                    o.status as order_status,
                    p.prescription_no,
                    p.external_prescription_no,
                    p.prescription_type,
                    p.hospital_type,
                    p.is_within,
                    p.dose_count,
                    o.patient_name,
                    o.patient_phone,
                    o.address_type,
                    o.batch_no,
                    o.delivery_time,
                    o.created_at as order_created_at,
                    latest_dispense.dispensed_at,
                    latest_dispense.dispenser,
                    latest_recheck.rechecked_at,
                    latest_recheck.rechecker,
                    pails.pail_nos,
                    o.order_remark,
                    o.updated_at
                from order_main o
                join prescription p on p.order_id = o.id
                join institution i on i.id = o.institution_id
                left join lateral (
                    select d.dispenser, d.dispensed_at
                    from dispense_record d
                    where d.order_id = o.id
                    order by d.dispensed_at desc, d.id desc
                    limit 1
                ) latest_dispense on true
                left join lateral (
                    select r.rechecker, r.rechecked_at
                    from prescription_recheck_record r
                    where r.order_id = o.id
                    order by r.rechecked_at desc, r.id desc
                    limit 1
                ) latest_recheck on true
                left join lateral (
                    select string_agg(x.pail_no, ',') as pail_nos
                    from (
                        select distinct dt.pail_no
                        from decoction_task dt
                        where dt.order_id = o.id
                          and (dt.prescription_id = p.id or dt.prescription_no = p.prescription_no)
                          and nullif(dt.pail_no, '') is not null
                        order by dt.pail_no
                    ) x
                ) pails on true
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by o.created_at desc, p.prescription_no desc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());

        return new AdminOrderRecheckPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminOrderRecheckItem, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public AdminOrderReviewPage searchAdminOrderReviews(AdminOrderReviewQuery query) {
        QueryParts filters = adminOrderReviewFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(distinct o.id)
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
                    o.receiver_name,
                    o.receiver_phone,
                    o.receiver_province,
                    o.receiver_city,
                    o.receiver_zone,
                    o.receiver_address,
                    o.address_type,
                    o.delivery_time,
                    o.created_at as order_created_at,
                    string_agg(distinct nullif(p.prescription_no, ''), ',') as prescription_nos,
                    string_agg(distinct nullif(p.external_prescription_no, ''), ',') as external_prescription_nos,
                    string_agg(distinct nullif(p.hospital_type, ''), ',') as hospital_types,
                    o.patient_name,
                    o.patient_phone,
                    string_agg(distinct nullif(p.prescription_type, ''), ',') as prescription_types,
                    string_agg(p.dose_count::text, ',' order by p.prescription_no)
                        filter (where p.dose_count is not null) as dose_counts,
                    count(distinct p.id)::int as prescription_count,
                    o.order_remark,
                    review_task.task_id as review_task_id,
                    review_task.task_status as review_task_status,
                    review_task.assigned_to as reviewer,
                    review_task.review_comment,
                    review_task.task_created_at,
                    review_task.task_completed_at,
                    o.updated_at
                from order_main o
                join prescription p on p.order_id = o.id
                join institution i on i.id = o.institution_id
                left join lateral (
                    select
                        t.id as task_id,
                        t.task_status,
                        t.assigned_to,
                        t.review_comment,
                        t.created_at as task_created_at,
                        t.completed_at as task_completed_at
                    from workflow_task t
                    where t.order_id = o.id
                      and t.task_type = 'ORDER_REVIEW'
                    order by t.created_at desc, t.id desc
                    limit 1
                ) review_task on true
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append("""
                 group by o.id, o.tenant_id, o.institution_id, i.institution_name, i.storage_type,
                          o.order_no, o.external_order_no, o.status, o.receiver_name, o.receiver_phone,
                          o.receiver_province, o.receiver_city, o.receiver_zone, o.receiver_address,
                          o.address_type, o.delivery_time, o.created_at, o.patient_name, o.patient_phone,
                          o.order_remark, review_task.task_id, review_task.task_status,
                          review_task.assigned_to, review_task.review_comment, review_task.task_created_at,
                          review_task.task_completed_at, o.updated_at
                 order by o.delivery_time asc nulls first, o.created_at asc, o.order_no asc
                 limit ? offset ?
                """);
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());

        return new AdminOrderReviewPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminOrderReviewItem, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public AdminOrderWarehousePage searchAdminOrderWarehouses(AdminOrderWarehouseQuery query) {
        QueryParts filters = adminOrderWarehouseFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(distinct o.id)
                from order_main o
                join prescription p on p.order_id = o.id
                join institution i on i.id = o.institution_id
                left join shipment s on s.order_id = o.id
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long totalValue = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());
        long total = totalValue == null ? 0 : totalValue;

        QueryParts listQuery = new QueryParts(adminOrderWarehouseSelectSql());
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append("""
                 group by o.id, o.tenant_id, o.order_no, o.external_order_no, o.status, o.created_at,
                          o.batch_no, i.institution_name, i.storage_type, o.address_type, o.receiver_name,
                          o.receiver_phone, o.delivery_time, o.receiver_province, o.receiver_city,
                          o.receiver_zone, o.receiver_address, o.patient_name, s.logistics_company,
                          s.logistics_no
                 order by o.created_at desc, max(p.prescription_no) desc limit ? offset ?
                """);
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());

        return new AdminOrderWarehousePage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminOrderWarehouseItem, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public List<AdminOrderWarehouseItem> exportAdminOrderWarehouses(AdminOrderWarehouseQuery query, int limit) {
        QueryParts filters = adminOrderWarehouseFilters(query);
        QueryParts listQuery = new QueryParts(adminOrderWarehouseSelectSql());
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append("""
                 group by o.id, o.tenant_id, o.order_no, o.external_order_no, o.status, o.created_at,
                          o.batch_no, i.institution_name, i.storage_type, o.address_type, o.receiver_name,
                          o.receiver_phone, o.delivery_time, o.receiver_province, o.receiver_city,
                          o.receiver_zone, o.receiver_address, o.patient_name, s.logistics_company,
                          s.logistics_no
                 order by o.created_at desc, max(p.prescription_no) desc limit ?
                """);
        listQuery.add(Math.max(1, limit));
        return jdbcTemplate.query(listQuery.sql(), this::mapAdminOrderWarehouseItem, listQuery.args());
    }

    public List<AdminOrderListItem> exportAdminOrders(AdminOrderSearchQuery query, int limit) {
        QueryParts filters = adminOrderFilters(query);
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
                    p.id as prescription_id,
                    p.status as prescription_status,
                    p.prescription_no as prescription_nos,
                    p.external_prescription_no as external_prescription_nos,
                    coalesce(p.prescription_type, '') as prescription_types,
                    coalesce(p.hospital_type, '') as hospital_types,
                    1 as prescription_count,
                    coalesce(pd.detail_count, 0) as detail_count,
                    p.dose_count,
                    p.is_within,
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
        listQuery.append(" order by o.created_at desc, p.prescription_no desc limit ?");
        listQuery.add(Math.max(1, limit));
        return jdbcTemplate.query(listQuery.sql(), this::mapAdminOrderListItem, listQuery.args());
    }

    public AdminOrderReceiptPage searchAdminOrderReceipts(AdminOrderReceiptQuery query, List<String> receiptStatuses) {
        QueryParts filters = adminOrderReceiptFilters(query, receiptStatuses);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from order_main o
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
                    o.order_no,
                    o.external_order_no,
                    i.institution_name,
                    o.receiver_name,
                    o.receiver_phone,
                    o.receiver_province,
                    o.receiver_city,
                    o.receiver_zone,
                    o.receiver_address,
                    o.patient_name,
                    coalesce(p.prescription_types, '') as prescription_types,
                    o.status as order_status,
                    latest_shipment.logistics_company,
                    latest_shipment.logistics_no,
                    latest_shipment.logistics_status,
                    o.created_at,
                    o.updated_at
                from order_main o
                join institution i on i.id = o.institution_id
                left join lateral (
                    select string_agg(distinct coalesce(p.prescription_type, ''), ',') as prescription_types
                    from prescription p
                    where p.order_id = o.id
                ) p on true
                left join lateral (
                    select s.logistics_company, s.logistics_no, s.logistics_status
                    from shipment s
                    where s.order_id = o.id
                    order by s.created_at desc
                    limit 1
                ) latest_shipment on true
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by o.created_at desc, o.order_no desc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());

        return new AdminOrderReceiptPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminOrderReceiptItem, listQuery.args()),
                total,
                query.page(),
                query.pageSize()
        );
    }

    public AdminPrescriptionReprintPage searchAdminPrescriptionReprints(
            AdminPrescriptionReprintQuery query,
            List<String> reprintStatuses
    ) {
        QueryParts filters = adminPrescriptionReprintFilters(query, reprintStatuses);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from order_main o
                join prescription p on p.order_id = o.id
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long total = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());

        QueryParts listQuery = new QueryParts("""
                select
                    o.id as order_id,
                    p.id as prescription_id,
                    o.order_no,
                    o.external_order_no,
                    o.status as order_status,
                    p.prescription_no,
                    p.external_prescription_no,
                    p.status as prescription_status,
                    i.institution_name,
                    o.patient_name,
                    o.patient_phone,
                    o.receiver_province,
                    o.receiver_city,
                    o.receiver_zone,
                    o.receiver_address,
                    o.address_type,
                    o.delivery_time,
                    o.created_at,
                    p.hospital_type,
                    p.prescription_type,
                    p.is_within,
                    p.dose_count,
                    o.batch_no,
                    latest_dispense.dispenser
                from order_main o
                join prescription p on p.order_id = o.id
                join institution i on i.id = o.institution_id
                left join lateral (
                    select d.dispenser
                    from dispense_record d
                    where d.order_id = o.id
                    order by d.dispensed_at desc, d.id desc
                    limit 1
                ) latest_dispense on true
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by o.created_at desc, p.prescription_no desc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());

        return new AdminPrescriptionReprintPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminPrescriptionReprintItem, listQuery.args()),
                total == null ? 0 : total,
                query.page(),
                query.pageSize()
        );
    }

    public AdminLabelPrintRecordPage searchAdminLabelPrintRecords(AdminLabelPrintRecordQuery query) {
        QueryParts filters = adminLabelPrintRecordFilters(query);
        QueryParts countQuery = new QueryParts("""
                select count(*)
                from label_print_record r
                where 1 = 1
                """);
        countQuery.append(filters.sql());
        countQuery.addAll(filters.argsList());
        Long total = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.args());

        QueryParts listQuery = new QueryParts("""
                select
                    r.id, r.tenant_id, r.order_id, r.prescription_id,
                    r.order_no, r.external_order_no, r.prescription_no, r.external_prescription_no,
                    r.institution_name, r.patient_name, r.print_status, r.print_channel,
                    r.printer_code, r.printer_name, r.provider, r.provider_task_no,
                    r.template_id, r.template_name, r.request_param, r.response_body,
                    r.failure_reason, r.operator, r.retry_of, r.created_at, r.updated_at
                from label_print_record r
                where 1 = 1
                """);
        listQuery.append(filters.sql());
        listQuery.addAll(filters.argsList());
        listQuery.append(" order by r.created_at desc, r.id desc limit ? offset ?");
        listQuery.add(query.pageSize());
        listQuery.add((query.page() - 1) * query.pageSize());

        return new AdminLabelPrintRecordPage(
                jdbcTemplate.query(listQuery.sql(), this::mapAdminLabelPrintRecord, listQuery.args()),
                total == null ? 0 : total,
                query.page(),
                query.pageSize()
        );
    }

    public AdminLabelPrintRecord insertAdminLabelPrintRecord(
            UUID id,
            UUID tenantId,
            UUID orderId,
            UUID prescriptionId,
            String orderNo,
            String externalOrderNo,
            String prescriptionNo,
            String externalPrescriptionNo,
            String institutionName,
            String patientName,
            String printStatus,
            String printChannel,
            String printerCode,
            String printerName,
            String provider,
            String providerTaskNo,
            UUID templateId,
            String templateName,
            String requestParam,
            String responseBody,
            String failureReason,
            String operator,
            UUID retryOf
    ) {
        String sql = """
                insert into label_print_record (
                    id, tenant_id, order_id, prescription_id,
                    order_no, external_order_no, prescription_no, external_prescription_no,
                    institution_name, patient_name, print_status, print_channel,
                    printer_code, printer_name, provider, provider_task_no,
                    template_id, template_name, request_param, response_body,
                    failure_reason, operator, retry_of
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id, tenant_id, order_id, prescription_id,
                          order_no, external_order_no, prescription_no, external_prescription_no,
                          institution_name, patient_name, print_status, print_channel,
                          printer_code, printer_name, provider, provider_task_no,
                          template_id, template_name, request_param, response_body,
                          failure_reason, operator, retry_of, created_at, updated_at
                """;
        return jdbcTemplate.queryForObject(
                sql,
                this::mapAdminLabelPrintRecord,
                id,
                tenantId,
                orderId,
                prescriptionId,
                orderNo,
                externalOrderNo,
                prescriptionNo,
                externalPrescriptionNo,
                institutionName,
                patientName,
                printStatus,
                printChannel,
                printerCode,
                printerName,
                provider,
                providerTaskNo,
                templateId,
                templateName,
                requestParam,
                responseBody,
                failureReason,
                operator,
                retryOf
        );
    }

    public Optional<String> findOrderNoByPrescriptionNo(String prescriptionNo) {
        String sql = """
                select o.order_no
                from order_main o
                join prescription p on p.order_id = o.id
                where p.prescription_no = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("order_no"), prescriptionNo)
                .stream()
                .findFirst();
    }

    public Optional<String> findPrescriptionNoByLegacyPdaRecipeId(String recipeId) {
        String sql = """
                select p.prescription_no
                from prescription p
                join order_main o on o.id = p.order_id
                where p.prescription_no = ?
                   or p.external_prescription_no = ?
                   or o.order_no = ?
                   or o.external_order_no = ?
                order by p.created_at asc, p.prescription_no asc
                limit 1
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("prescription_no"),
                        recipeId, recipeId, recipeId, recipeId)
                .stream()
                .findFirst();
    }

    private QueryParts adminOperatorFilters(AdminOperatorQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        u.username ilike ?
                        or u.display_name ilike ?
                        or u.role_code ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        String roleCode = query.roleCode() == null || query.roleCode().isBlank() ? null : query.roleCode().trim();
        if (roleCode != null) {
            filters.append(" and u.role_code = ?");
            filters.add(roleCode);
        }
        if (query.enabled() != null) {
            filters.append(" and u.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminOperatorRoleFilters(AdminOperatorRoleQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append(" and u.role_code ilike ?");
            filters.add("%" + keyword + "%");
        }
        return filters;
    }

    private QueryParts adminDictTypeFilters(AdminDictTypeQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        t.type_code ilike ?
                        or t.type_name ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.enabled() != null) {
            filters.append(" and t.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminDictItemFilters(AdminDictItemQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        i.item_code ilike ?
                        or i.item_name ilike ?
                        or coalesce(i.item_value, '') ilike ?
                        or t.type_code ilike ?
                        or t.type_name ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.typeId() != null) {
            filters.append(" and i.type_id = ?");
            filters.add(query.typeId());
        }
        if (query.enabled() != null) {
            filters.append(" and i.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminSystemConfigFilters(AdminSystemConfigQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        c.config_key ilike ?
                        or c.config_name ilike ?
                        or c.config_value ilike ?
                        or coalesce(c.remark, '') ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        filters.addEqualsFilter("c.value_type", query.valueType());
        if (query.enabled() != null) {
            filters.append(" and c.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminDecoctCenterFilters(AdminDecoctCenterQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        c.center_code ilike ?
                        or c.center_name ilike ?
                        or coalesce(c.contact_name, '') ilike ?
                        or coalesce(c.contact_phone, '') ilike ?
                        or coalesce(c.address, '') ilike ?
                        or coalesce(c.remark, '') ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.enabled() != null) {
            filters.append(" and c.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminHerbFilters(AdminHerbQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        h.herb_code ilike ?
                        or h.herb_name ilike ?
                        or coalesce(h.drug_specs, '') ilike ?
                        or coalesce(h.drug_origin, '') ilike ?
                        or coalesce(h.unit, '') ilike ?
                        or coalesce(h.remark, '') ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.enabled() != null) {
            filters.append(" and h.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminHerbAreaFilters(AdminHerbAreaQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        a.area_code ilike ?
                        or a.area_name ilike ?
                        or coalesce(a.remark, '') ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.enabled() != null) {
            filters.append(" and a.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminHerbIndexFilters(AdminHerbIndexQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        x.external_herb_code ilike ?
                        or x.external_herb_name ilike ?
                        or i.institution_code ilike ?
                        or i.institution_name ilike ?
                        or h.herb_code ilike ?
                        or h.herb_name ilike ?
                        or coalesce(x.remark, '') ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.institutionId() != null) {
            filters.append(" and x.institution_id = ?");
            filters.add(query.institutionId());
        }
        if (query.enabled() != null) {
            filters.append(" and x.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminHerbIndexOperationLogFilters(AdminHerbIndexOperationLogQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        l.external_herb_code ilike ?
                        or l.external_herb_name ilike ?
                        or l.institution_code ilike ?
                        or l.institution_name ilike ?
                        or l.herb_code ilike ?
                        or l.herb_name ilike ?
                        or coalesce(l.remark, '') ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.institutionId() != null) {
            filters.append(" and l.institution_id = ?");
            filters.add(query.institutionId());
        }
        filters.addEqualsFilter("l.action_type", query.actionType());
        return filters;
    }

    private QueryParts adminInstitutionFilters(AdminInstitutionQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        i.institution_code ilike ?
                        or i.institution_name ilike ?
                        or i.storage_type ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        filters.addEqualsFilter("i.status", query.status());
        filters.addEqualsFilter("i.institution_type", query.institutionType());
        return filters;
    }

    private QueryParts adminInstitutionAppFilters(AdminInstitutionAppQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        i.institution_code ilike ?
                        or i.institution_name ilike ?
                        or a.app_key ilike ?
                        or a.callback_url ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.institutionId() != null) {
            filters.append(" and a.institution_id = ?");
            filters.add(query.institutionId());
        }
        if (query.enabled() != null) {
            filters.append(" and a.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminInstitutionApiFilters(AdminInstitutionApiQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        a.api_code ilike ?
                        or a.api_name ilike ?
                        or a.request_path ilike ?
                        or a.description ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.enabled() != null) {
            filters.append(" and a.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminInstitutionApiPermissionFilters(AdminInstitutionApiPermissionQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        i.institution_code ilike ?
                        or i.institution_name ilike ?
                        or a.api_code ilike ?
                        or a.api_name ilike ?
                        or a.request_path ilike ?
                        or p.remark ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.institutionId() != null) {
            filters.append(" and p.institution_id = ?");
            filters.add(query.institutionId());
        }
        if (query.apiId() != null) {
            filters.append(" and p.api_id = ?");
            filters.add(query.apiId());
        }
        if (query.enabled() != null) {
            filters.append(" and p.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminInstitutionIpWhitelistFilters(AdminInstitutionIpWhitelistQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        i.institution_code ilike ?
                        or i.institution_name ilike ?
                        or w.ip_range ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.institutionId() != null) {
            filters.append(" and w.institution_id = ?");
            filters.add(query.institutionId());
        }
        filters.addLikeFilter("w.ip_range", query.ipRange());
        if (query.enabled() != null) {
            filters.append(" and w.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminLogisticsSpecialRuleFilters(AdminLogisticsSpecialRuleQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        i.institution_code ilike ?
                        or i.institution_name ilike ?
                        or r.rule_name ilike ?
                        or r.logistics_company ilike ?
                        or r.remark ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.institutionId() != null) {
            filters.append(" and r.institution_id = ?");
            filters.add(query.institutionId());
        }
        if (query.enabled() != null) {
            filters.append(" and r.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminLogisticsAddressCostFilters(AdminLogisticsAddressCostQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        i.institution_code ilike ?
                        or i.institution_name ilike ?
                        or c.logistics_company ilike ?
                        or c.province ilike ?
                        or c.city ilike ?
                        or c.district ilike ?
                        or c.remark ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.institutionId() != null) {
            filters.append(" and c.institution_id = ?");
            filters.add(query.institutionId());
        }
        filters.addLikeFilter("c.logistics_company", query.logisticsCompany());
        if (query.enabled() != null) {
            filters.append(" and c.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminOrderMergeFilters(AdminOrderMergeQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        m.merge_no ilike ?
                        or m.logistics_company ilike ?
                        or m.logistics_no ilike ?
                        or m.remark ilike ?
                        or exists (
                            select 1
                            from order_merge_item mi
                            join order_main o on o.id = mi.order_id
                            join institution i on i.id = o.institution_id
                            where mi.merge_id = m.id
                              and (mi.order_no ilike ? or i.institution_name ilike ?)
                        )
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        filters.addEqualsFilter("m.status", query.status());
        return filters;
    }

    private QueryParts adminOrderInterceptRuleFilters(AdminOrderInterceptRuleQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        r.rule_code ilike ?
                        or r.rule_name ilike ?
                        or r.match_field ilike ?
                        or r.match_value ilike ?
                        or r.reason ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        filters.addEqualsFilter("r.intercept_stage", query.interceptStage());
        if (query.enabled() != null) {
            filters.append(" and r.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminLabelTemplateFilters(AdminLabelTemplateQuery query) {
        QueryParts filters = new QueryParts("");
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        if (keyword != null) {
            filters.append("""
                     and (
                        t.template_code ilike ?
                        or t.template_name ilike ?
                        or t.content_template ilike ?
                        or coalesce(i.name, '') ilike ?
                    )
                    """);
            String pattern = "%" + keyword + "%";
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
            filters.add(pattern);
        }
        if (query.institutionId() != null) {
            filters.append(" and t.institution_id = ?");
            filters.add(query.institutionId());
        }
        filters.addEqualsFilter("t.prescription_type", query.prescriptionType());
        if (query.enabled() != null) {
            filters.append(" and t.enabled = ?");
            filters.add(query.enabled());
        }
        return filters;
    }

    private QueryParts adminOrderFilters(AdminOrderSearchQuery query) {
        QueryParts filters = new QueryParts("");
        filters.addRangeFilter("o.created_at", query.startTime(), query.endTime());
        filters.addLikeFilter("i.institution_name", query.institution());
        filters.addLikeFilter("i.storage_type", query.decoctionCenter());
        filters.addEqualsFilter("o.status", query.orderStatus());
        filters.addNotEqualsFilter("o.status", query.excludeOrderStatus());
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

    private QueryParts adminManualProcessFilters(AdminManualProcessQuery query) {
        QueryParts filters = new QueryParts("");
        String processType = query.processType() == null || query.processType().isBlank()
                ? "PENDING"
                : query.processType().trim();
        if (!"PENDING".equals(processType)) {
            filters.addRangeFilter("o.created_at", query.startTime(), query.endTime());
        }
        filters.addLikeFilter("i.institution_name", query.institution());
        filters.addEqualsFilter("p.prescription_type", query.prescriptionType());
        filters.addEqualsFilter("p.hospital_type", query.hospitalType());
        if (query.isWithin() != null) {
            filters.append(" and p.is_within = ?");
            filters.add(query.isWithin());
        }
        filters.addEqualsFilter("o.address_type", query.deliveryType());
        filters.addLikeFilter("o.order_no", query.orderNo());
        filters.addLikeFilter("p.prescription_no", query.prescriptionNo());
        filters.addLikeFilter("p.external_prescription_no", query.hospitalPrescriptionNo());
        filters.addLikeFilter("o.patient_name", query.patientName());
        if ("NOT_DUE".equals(processType)) {
            filters.append(" and o.status = 'CREATED' and o.delivery_time is not null and o.delivery_time > now()");
        } else if ("PROCESSED".equals(processType)) {
            filters.append("""
                     and o.status in (
                        'AUDIT_PASSED', 'RECHECKED', 'DECOCTING', 'DECOCTED', 'PACKED',
                        'SHIPPED', 'IN_TRANSIT', 'SIGNED'
                    )
                    """);
        } else {
            filters.append(" and o.status = 'CREATED' and (o.delivery_time is null or o.delivery_time <= now())");
        }
        if ("LOW".equals(query.doseRange()) || "1".equals(query.doseRange())) {
            filters.append(" and p.dose_count < 3");
        } else if ("HIGH".equals(query.doseRange()) || "2".equals(query.doseRange())) {
            filters.append(" and p.dose_count >= 3");
        }
        return filters;
    }

    private QueryParts adminOrderRecheckFilters(AdminOrderRecheckQuery query) {
        QueryParts filters = new QueryParts("");
        filters.addRangeFilter("o.created_at", query.startTime(), query.endTime());
        filters.addLikeFilter("i.institution_name", query.institution());
        filters.addEqualsFilter("p.prescription_type", query.prescriptionType());
        filters.addEqualsFilter("p.hospital_type", query.hospitalType());
        filters.addEqualsFilter("o.address_type", query.deliveryType());
        filters.addEqualsFilter("o.batch_no", query.batchNo());
        filters.addLikeFilter("p.prescription_no", query.prescriptionNo());
        if (query.isWithin() != null) {
            filters.append(" and p.is_within = ?");
            filters.add(query.isWithin());
        }
        String dispenser = filters.trimmed(query.dispenser());
        if (dispenser != null) {
            filters.append("""
                     and exists (
                        select 1
                        from dispense_record d
                        where d.order_id = o.id
                          and d.dispenser ilike ?
                    )
                    """);
            filters.add("%" + dispenser + "%");
        }
        String rechecker = filters.trimmed(query.rechecker());
        if (rechecker != null) {
            filters.append("""
                     and exists (
                        select 1
                        from prescription_recheck_record r
                        where r.order_id = o.id
                          and r.rechecker ilike ?
                    )
                    """);
            filters.add("%" + rechecker + "%");
        }
        filters.append("""
                 and exists (
                    select 1
                    from dispense_record d
                    where d.order_id = o.id
                )
                """);
        if ("RECHECKED".equals(query.recheckStatus())) {
            filters.append("""
                     and exists (
                        select 1
                        from prescription_recheck_record r
                        where r.order_id = o.id
                    )
                    """);
        } else {
            filters.append("""
                     and o.status = 'AUDIT_PASSED'
                     and not exists (
                        select 1
                        from prescription_recheck_record r
                        where r.order_id = o.id
                    )
                    """);
        }
        return filters;
    }

    private QueryParts adminOrderReviewFilters(AdminOrderReviewQuery query) {
        QueryParts filters = new QueryParts("");
        filters.addRangeFilter("o.created_at", query.startTime(), query.endTime());
        filters.addLikeFilter("i.institution_name", query.institution());
        filters.addEqualsFilter("p.prescription_type", query.prescriptionType());
        filters.addEqualsFilter("p.hospital_type", query.hospitalType());
        filters.addEqualsFilter("o.address_type", query.deliveryType());
        filters.addLikeFilter("o.order_no", query.orderNo());
        filters.addLikeFilter("p.prescription_no", query.prescriptionNo());
        filters.addLikeFilter("p.external_prescription_no", query.hospitalPrescriptionNo());
        filters.addLikeFilter("o.patient_name", query.patientName());
        if (query.isWithin() != null) {
            filters.append(" and p.is_within = ?");
            filters.add(query.isWithin());
        }
        if ("NOT_DUE".equals(query.reviewStatus())) {
            filters.append(" and o.status = 'CREATED' and o.delivery_time is not null and o.delivery_time > now()");
        } else if ("REVIEWED".equals(query.reviewStatus())) {
            filters.append("""
                     and o.status in (
                        'AUDIT_PASSED', 'RECHECKED', 'DECOCTING', 'DECOCTED', 'PACKED',
                        'SHIPPED', 'IN_TRANSIT', 'SIGNED'
                    )
                    """);
        } else {
            filters.append(" and o.status = 'CREATED' and (o.delivery_time is null or o.delivery_time <= now())");
        }
        if ("LOW".equals(query.doseRange()) || "1".equals(query.doseRange())) {
            filters.append(" and p.dose_count < 3");
        } else if ("HIGH".equals(query.doseRange()) || "2".equals(query.doseRange())) {
            filters.append(" and p.dose_count >= 3");
        }
        return filters;
    }

    private QueryParts adminOrderWarehouseFilters(AdminOrderWarehouseQuery query) {
        QueryParts filters = new QueryParts("");
        filters.addInFilter("o.status", List.of("RECHECKED", "DECOCTING", "DECOCTED"));
        filters.addRangeFilter("o.created_at", query.startTime(), query.endTime());
        filters.addLikeFilter("i.institution_name", query.institution());
        filters.addEqualsFilter("p.prescription_type", query.prescriptionType());
        filters.addEqualsFilter("p.hospital_type", query.hospitalType());
        filters.addEqualsFilter("o.status", query.orderStatus());
        filters.addLikeFilter("i.storage_type", query.decoctionCenter());
        filters.addEqualsFilter("o.address_type", query.deliveryType());
        filters.addLikeFilter("s.logistics_company", query.logisticsCompany());
        filters.addLikeFilter("o.receiver_province", query.province());
        filters.addLikeFilter("o.order_no", query.orderNo());
        filters.addLikeFilter("p.prescription_no", query.prescriptionNo());
        filters.addLikeFilter("p.external_prescription_no", query.hospitalPrescriptionNo());
        filters.addLikeFilter("o.patient_name", query.patientName());
        filters.addLikeFilter("o.receiver_phone", query.receiverPhone());
        if ("1".equals(query.nodeTime())) {
            filters.append(" and o.created_at <= date_trunc('day', now()) + interval '12 hours'");
        } else if ("5".equals(query.nodeTime())) {
            filters.append("""
                     and o.created_at >= date_trunc('day', now()) + interval '12 hours'
                     and o.created_at <= date_trunc('day', now()) + interval '1 day' - interval '1 second'
                    """);
        } else if ("21".equals(query.nodeTime())) {
            filters.append("""
                     and o.created_at >= date_trunc('day', now())
                     and o.created_at <= date_trunc('day', now()) + interval '1 day' - interval '1 second'
                    """);
        }
        return filters;
    }

    private QueryParts adminOrderReceiptFilters(AdminOrderReceiptQuery query, List<String> receiptStatuses) {
        QueryParts filters = new QueryParts("");
        filters.addInFilter("o.status", receiptStatuses);
        filters.addReceiptPrescriptionFilter(query.prescriptionNo());
        filters.addLikeFilter("o.receiver_name", query.receiverName());
        filters.addLikeFilter("o.receiver_phone", query.receiverPhone());
        filters.addLikeFilter("o.patient_name", query.patientName());
        return filters;
    }

    private QueryParts adminPrescriptionReprintFilters(
            AdminPrescriptionReprintQuery query,
            List<String> reprintStatuses
    ) {
        QueryParts filters = new QueryParts("");
        filters.addInFilter("o.status", reprintStatuses);
        filters.addRangeFilter("o.created_at", query.startTime(), query.endTime());
        filters.addLikeFilter("p.prescription_no", query.prescriptionNo());
        return filters;
    }

    private QueryParts adminLabelPrintRecordFilters(AdminLabelPrintRecordQuery query) {
        QueryParts filters = new QueryParts("");
        if (query.printStatus() != null && !query.printStatus().isBlank()) {
            filters.append(" and r.print_status = ?");
            filters.add(query.printStatus().trim());
        }
        if (query.prescriptionNo() != null && !query.prescriptionNo().isBlank()) {
            filters.append(" and r.prescription_no ilike ?");
            filters.add("%" + query.prescriptionNo().trim() + "%");
        }
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
            BigDecimal logisticsFee,
            BigDecimal discountAmount,
            String legacyCompanyNum,
            String storageType,
            String createIp,
            Instant orderTime,
            String classes,
            BigDecimal orderPkgWeight,
            Integer orderPkgNum,
            BigDecimal logisticsReceivablesMoney,
            String logisticsPayMethod,
            String logisticsType,
            String logisticsMode,
            String spOrderId,
            String logisId,
            String areaLevel,
            String routeCode,
            String baseProductNo,
            Instant packageTime,
            Instant outboundTime,
            Instant signTime,
            String rawPayload
    ) {
        String sql = """
                insert into order_main (
                    id, tenant_id, institution_id, order_no, external_order_no, status,
                    patient_name, patient_phone, receiver_name, receiver_phone, receiver_province,
                    receiver_city, receiver_zone, receiver_address, address_type, delivery_time,
                    batch_no, order_remark, callback_url, logistics_fee, discount_amount,
                    legacy_company_num, storage_type, create_ip, order_time, classes,
                    order_pkg_weight, order_pkg_num, logistics_receivables_money, logistics_pay_method,
                    logistics_type, logistics_mode, sp_order_id, logis_id, area_level, route_code,
                    base_product_no, package_time, outbound_time, sign_time, raw_payload
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        jdbcTemplate.update(sql, id, tenantId, institutionId, orderNo, externalOrderNo, status,
                patientName, patientPhone, receiverName, receiverPhone, receiverProvince, receiverCity, receiverZone,
                receiverAddress, addressType, offsetDateTime(deliveryTime), batchNo, orderRemark, callbackUrl,
                logisticsFee, discountAmount, legacyCompanyNum, storageType, createIp, offsetDateTime(orderTime),
                classes, orderPkgWeight, orderPkgNum, logisticsReceivablesMoney, logisticsPayMethod,
                logisticsType, logisticsMode, spOrderId, logisId, areaLevel, routeCode, baseProductNo,
                offsetDateTime(packageTime), offsetDateTime(outboundTime), offsetDateTime(signTime), rawPayload);
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
            Integer boilTimes,
            Integer isWithin,
            Integer perPackNum,
            Integer perPackDose,
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
        insertPrescription(id, tenantId, institutionId, orderId, prescriptionNo, externalPrescriptionNo,
                prescriptionType, status, hospitalType, doseCount, decoctionCount, boilTimes, isWithin,
                perPackNum, perPackDose, decoctionUnitPrice, decoctionTotalPrice, totalAmount, doctorName,
                diagnosis, departmentName, wardName, bedNo, medicationMethod, medicationInstruction,
                prescriptionRemark, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, rawPayload);
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
            Integer boilTimes,
            Integer isWithin,
            Integer perPackNum,
            Integer perPackDose,
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
            String patientAge,
            String patientMonthAge,
            String patientDayAge,
            String patientGender,
            String patientCardNo,
            String treatCard,
            String patientTel,
            String isPregnant,
            String herbType,
            String wjType,
            String doctorTel,
            String hospitalName,
            String hospitalNum,
            String orderHandleFloor,
            String jyjDecoctionPlan,
            String jyjDecoctionAdvice,
            String labelSize,
            String bindNo,
            BigDecimal drugsMoney,
            String auditFlowPicUrl,
            String auditReason,
            String auditResult,
            String dispenseFlowPicUrl,
            String recheckFlowPicUrl,
            String rawPayload
    ) {
        String sql = """
                insert into prescription (
                    id, tenant_id, institution_id, order_id, prescription_no,
                    external_prescription_no, prescription_type, status, hospital_type, dose_count,
                    decoction_count, boil_times, is_within, per_pack_num, per_pack_dose,
                    decoction_unit_price, decoction_total_price, total_amount,
                    doctor_name, diagnosis, department_name, ward_name, bed_no, medication_method,
                    medication_instruction, prescription_remark, patient_age, patient_month_age,
                    patient_day_age, patient_gender, patient_card_no, treat_card, patient_tel,
                    is_pregnant, herb_type, wj_type, doctor_tel, hospital_name, hospital_num,
                    order_handle_floor, jyj_decoction_plan, jyj_decoction_advice, label_size,
                    bind_no, drugs_money, audit_flow_pic_url, audit_reason, audit_result,
                    dispense_flow_pic_url, recheck_flow_pic_url, raw_payload
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        jdbcTemplate.update(sql, id, tenantId, institutionId, orderId, prescriptionNo,
                externalPrescriptionNo, prescriptionType, status, hospitalType, doseCount, decoctionCount,
                boilTimes, isWithin, perPackNum, perPackDose, decoctionUnitPrice, decoctionTotalPrice,
                totalAmount, doctorName, diagnosis, departmentName,
                wardName, bedNo, medicationMethod, medicationInstruction, prescriptionRemark,
                patientAge, patientMonthAge, patientDayAge, patientGender, patientCardNo, treatCard,
                patientTel, isPregnant, herbType, wjType, doctorTel, hospitalName, hospitalNum,
                orderHandleFloor, jyjDecoctionPlan, jyjDecoctionAdvice, labelSize, bindNo, drugsMoney,
                auditFlowPicUrl, auditReason, auditResult, dispenseFlowPicUrl, recheckFlowPicUrl, rawPayload);
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
        insertPrescriptionDetail(id, tenantId, prescriptionId, drugCode, drugName, platformDrugCode,
                platformDrugName, drugSpecs, drugOrigin, dose, unit, specialUsage, quantity, unitPrice,
                settlementUnitPrice, totalPrice, settlementTotalPrice, batchNo, remark, validationTips,
                platformDrugCode, platformDrugName, null, null, null, null, null, null, null, sortNo);
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
            String dcGoodsNum,
            String dcGoodsName,
            String rootsGoodsName,
            String supplierName,
            BigDecimal medPerDose,
            BigDecimal medPerDay,
            String detailStatus,
            String note,
            BigDecimal waterAbsorbRatio,
            int sortNo
    ) {
        String sql = """
                insert into prescription_detail (
                    id, tenant_id, prescription_id, drug_code, drug_name, platform_drug_code,
                    platform_drug_name, drug_specs, drug_origin, dose, unit, special_usage, quantity,
                    unit_price, settlement_unit_price, total_price, settlement_total_price,
                    batch_no, remark, validation_tips, dc_goods_num, dc_goods_name, roots_goods_name,
                    supplier_name, med_per_dose, med_per_day, detail_status, note, water_absorb_ratio, sort_no
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, tenantId, prescriptionId, drugCode, drugName, platformDrugCode, platformDrugName,
                drugSpecs, drugOrigin, dose, unit, specialUsage, quantity, unitPrice, settlementUnitPrice, totalPrice,
                settlementTotalPrice, batchNo, remark, validationTips, dcGoodsNum, dcGoodsName, rootsGoodsName,
                supplierName, medPerDose, medPerDay, detailStatus, note, waterAbsorbRatio, sortNo);
    }

    public int updateOrderStatus(UUID orderId, String status) {
        String sql = """
                update order_main
                set status = ?, updated_at = now()
                where id = ?
                """;
        return jdbcTemplate.update(sql, status, orderId);
    }

    public int updateOrderStatusIfCurrent(UUID orderId, String currentStatus, String targetStatus) {
        return updateOrderStatusIfCurrent(orderId, currentStatus, targetStatus, null);
    }

    public int updateOrderStatusIfCurrent(
            UUID orderId,
            String currentStatus,
            String targetStatus,
            String batchNo
    ) {
        String sql = """
                update order_main
                set status = ?,
                    batch_no = coalesce(nullif(?, ''), batch_no),
                    updated_at = now()
                where id = ?
                  and status = ?
                """;
        return jdbcTemplate.update(sql, targetStatus, batchNo, orderId, currentStatus);
    }

    public int updateOrderStatusAndAppendRemarkIfCurrent(
            UUID orderId,
            String currentStatus,
            String targetStatus,
            String remark
    ) {
        String sql = """
                update order_main
                set status = ?,
                    order_remark = case
                        when nullif(?, '') is null then order_remark
                        when nullif(order_remark, '') is null then ?
                        else order_remark || '|' || ?
                    end,
                    updated_at = now()
                where id = ?
                  and status = ?
                """;
        return jdbcTemplate.update(sql, targetStatus, remark, remark, remark, orderId, currentStatus);
    }

    public int completeManualProcessPrescriptionStatuses(UUID orderId) {
        String sql = """
                update prescription
                set status = case
                        when prescription_type in ('2', 'DECOCTION') then 'DECOCTED'
                        else 'RECHECKED'
                    end,
                    updated_at = now()
                where order_id = ?
                  and status <> 'CANCELLED'
                """;
        return jdbcTemplate.update(sql, orderId);
    }

    public int updateLatestShipmentToSigned(UUID orderId) {
        String sql = """
                update shipment
                set logistics_status = 'SIGNED',
                    sign_time = coalesce(sign_time, now()),
                    updated_at = now()
                where id = (
                    select id
                    from shipment
                    where order_id = ?
                    order by created_at desc
                    limit 1
                )
                  and logistics_status <> 'SIGNED'
                """;
        return jdbcTemplate.update(sql, orderId);
    }

    public int updatePrescriptionsStatusByOrderId(UUID orderId, String status) {
        String sql = """
                update prescription
                set status = ?,
                    updated_at = now()
                where order_id = ?
                  and status <> ?
                """;
        return jdbcTemplate.update(sql, status, orderId, status);
    }

    public int updatePrescriptionStatus(UUID orderId, UUID prescriptionId, String status) {
        String sql = """
                update prescription
                set status = ?,
                    updated_at = now()
                where order_id = ?
                  and id = ?
                  and status <> ?
                """;
        return jdbcTemplate.update(sql, status, orderId, prescriptionId, status);
    }

    public int countActivePrescriptionsByOrderId(UUID orderId) {
        String sql = """
                select count(*)
                from prescription
                where order_id = ?
                  and status <> 'CANCELLED'
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, orderId);
        return count == null ? 0 : count;
    }

    public int cancelPendingWorkflowTasks(UUID orderId, String operator, String reason) {
        String sql = """
                update workflow_task
                set task_status = 'CANCELLED',
                    assigned_to = ?,
                    review_comment = ?,
                    completed_at = now(),
                    updated_at = now()
                where order_id = ?
                  and task_status = 'PENDING'
                """;
        return jdbcTemplate.update(sql, operator, reason, orderId);
    }

    public int cancelActiveDecoctionTasksByOrderId(UUID orderId, String operator, String reason) {
        String sql = """
                update decoction_task
                set task_status = 'CANCELLED',
                    cancel_operation_id = coalesce(cancel_operation_id, ?),
                    cancelled_at = coalesce(cancelled_at, now()),
                    operator = ?,
                    updated_at = now()
                where order_id = ?
                  and task_status in ('BOUND', 'DECOCTING', 'DECOCTED')
                """;
        String operationId = "ORDER-INIT-" + UUID.randomUUID();
        return jdbcTemplate.update(sql, operationId, operator, orderId);
    }

    public int deleteShipmentRuntimeByOrderId(UUID orderId) {
        jdbcTemplate.update("delete from shipment_trace where order_id = ?", orderId);
        return jdbcTemplate.update("delete from shipment where order_id = ?", orderId);
    }

    public List<UUID> findPrescriptionIdsByOrderId(UUID orderId) {
        String sql = """
                select id
                from prescription
                where order_id = ?
                order by created_at asc, prescription_no asc
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getObject("id", UUID.class), orderId);
    }

    public int updateOrderAddress(
            UUID orderId,
            String receiverName,
            String receiverPhone,
            String receiverProvince,
            String receiverCity,
            String receiverZone,
            String receiverAddress,
            String addressType,
            Instant deliveryTime
    ) {
        String sql = """
                update order_main
                set receiver_name = ?,
                    receiver_phone = ?,
                    receiver_province = ?,
                    receiver_city = ?,
                    receiver_zone = ?,
                    receiver_address = ?,
                    address_type = ?,
                    delivery_time = ?,
                    updated_at = now()
                where id = ?
                """;
        return jdbcTemplate.update(sql, receiverName, receiverPhone, receiverProvince, receiverCity, receiverZone,
                receiverAddress, addressType, offsetDateTime(deliveryTime), orderId);
    }

    public int updateOrderRemark(UUID orderId, String orderRemark) {
        String sql = """
                update order_main
                set order_remark = ?,
                    updated_at = now()
                where id = ?
                """;
        return jdbcTemplate.update(sql, orderRemark, orderId);
    }

    public int updatePrescription(
            UUID orderId,
            UUID prescriptionId,
            String prescriptionType,
            String hospitalType,
            Integer doseCount,
            Integer decoctionCount,
            Integer boilTimes,
            Integer isWithin,
            Integer perPackNum,
            Integer perPackDose,
            String medicationMethod,
            String medicationInstruction,
            String prescriptionRemark
    ) {
        String sql = """
                update prescription
                set prescription_type = ?,
                    hospital_type = ?,
                    dose_count = ?,
                    decoction_count = ?,
                    boil_times = ?,
                    is_within = ?,
                    per_pack_num = ?,
                    per_pack_dose = ?,
                    medication_method = ?,
                    medication_instruction = ?,
                    prescription_remark = ?,
                    updated_at = now()
                where id = ?
                  and order_id = ?
                """;
        return jdbcTemplate.update(
                sql,
                prescriptionType,
                hospitalType,
                doseCount,
                decoctionCount,
                boilTimes,
                isWithin,
                perPackNum,
                perPackDose,
                medicationMethod,
                medicationInstruction,
                prescriptionRemark,
                prescriptionId,
                orderId
        );
    }

    public void insertOperationLog(
            UUID id,
            UUID tenantId,
            UUID orderId,
            UUID prescriptionId,
            String operator,
            String action,
            String result,
            String reason,
            String payload
    ) {
        String sql = """
                insert into operation_log (
                    id, tenant_id, order_id, prescription_id, operator, action, result, reason, payload
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        jdbcTemplate.update(sql, id, tenantId, orderId, prescriptionId, operator, action, result, reason, payload);
    }

    public int insertCompletedWorkflowTask(
            UUID taskId,
            UUID tenantId,
            UUID orderId,
            String taskType,
            String taskStatus,
            String sourceEventId,
            String assignedTo,
            String comment,
            String payload,
            Instant completedAt
    ) {
        String sql = """
                insert into workflow_task (
                    id, tenant_id, order_id, task_type, task_status, source_event_id,
                    assigned_to, review_comment, payload, completed_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())
                on conflict do nothing
                """;
        return jdbcTemplate.update(sql, taskId, tenantId, orderId, taskType, taskStatus, sourceEventId,
                assignedTo, comment, payload, offsetDateTime(completedAt));
    }

    public int insertDispenseRecord(
            UUID recordId,
            UUID tenantId,
            UUID orderId,
            UUID prescriptionId,
            UUID taskId,
            String dispenser,
            String comment,
            Instant dispensedAt
    ) {
        String sql = """
                insert into dispense_record (
                    id, tenant_id, order_id, prescription_id, task_id, dispenser, dispense_comment, print_status, dispensed_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'PRINTED', ?)
                on conflict (task_id) do nothing
                """;
        return jdbcTemplate.update(sql, recordId, tenantId, orderId, prescriptionId, taskId, dispenser, comment,
                offsetDateTime(dispensedAt));
    }

    public int insertPrescriptionAuditRecord(
            UUID recordId,
            UUID tenantId,
            UUID orderId,
            UUID prescriptionId,
            UUID taskId,
            String auditor,
            String comment,
            Instant auditedAt
    ) {
        String sql = """
                insert into prescription_audit_record (
                    id, tenant_id, order_id, prescription_id, task_id, audit_result, auditor, audit_comment, audited_at
                ) values (?, ?, ?, ?, ?, 'PASSED', ?, ?, ?)
                on conflict (task_id) do nothing
                """;
        return jdbcTemplate.update(sql, recordId, tenantId, orderId, prescriptionId, taskId, auditor, comment,
                offsetDateTime(auditedAt));
    }

    public int insertPrescriptionRecheckRecord(
            UUID recordId,
            UUID tenantId,
            UUID orderId,
            UUID prescriptionId,
            UUID taskId,
            String rechecker,
            String comment,
            Instant recheckedAt
    ) {
        String sql = """
                insert into prescription_recheck_record (
                    id, tenant_id, order_id, prescription_id, task_id, recheck_result, rechecker, recheck_comment, rechecked_at
                ) values (?, ?, ?, ?, ?, 'PASSED', ?, ?, ?)
                on conflict (task_id) do nothing
                """;
        return jdbcTemplate.update(sql, recordId, tenantId, orderId, prescriptionId, taskId, rechecker, comment,
                offsetDateTime(recheckedAt));
    }

    public int upsertPrescriptionDecoctionProfile(
            UUID id,
            UUID tenantId,
            UUID orderId,
            UUID prescriptionId,
            String realWater,
            String soakOperator,
            Instant soakTimeStart,
            Instant boilTimeStart,
            Instant boilTimeEnd,
            String boilOperator,
            Instant outMedTimeStart,
            Instant outMedTimeEnd,
            String outMedOperator,
            Instant packTimeStart,
            Instant packTimeEnd,
            String packOperator,
            String profilePayload
    ) {
        String sql = """
                insert into prescription_decoction_profile (
                    id, tenant_id, order_id, prescription_id, real_water, soak_operator, soak_time_start,
                    boil_time_start, boil_time_end, boil_operator, out_med_time_start, out_med_time_end,
                    out_med_operator, pack_time_start, pack_time_end, pack_operator, profile_payload
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                on conflict (tenant_id, prescription_id) do update
                set real_water = excluded.real_water,
                    soak_operator = excluded.soak_operator,
                    soak_time_start = excluded.soak_time_start,
                    boil_time_start = excluded.boil_time_start,
                    boil_time_end = excluded.boil_time_end,
                    boil_operator = excluded.boil_operator,
                    out_med_time_start = excluded.out_med_time_start,
                    out_med_time_end = excluded.out_med_time_end,
                    out_med_operator = excluded.out_med_operator,
                    pack_time_start = excluded.pack_time_start,
                    pack_time_end = excluded.pack_time_end,
                    pack_operator = excluded.pack_operator,
                    profile_payload = excluded.profile_payload,
                    updated_at = now()
                """;
        return jdbcTemplate.update(sql, id, tenantId, orderId, prescriptionId, realWater, soakOperator,
                offsetDateTime(soakTimeStart), offsetDateTime(boilTimeStart), offsetDateTime(boilTimeEnd),
                boilOperator, offsetDateTime(outMedTimeStart), offsetDateTime(outMedTimeEnd), outMedOperator,
                offsetDateTime(packTimeStart), offsetDateTime(packTimeEnd), packOperator, profilePayload);
    }

    public int insertCompletedDecoctionTask(
            UUID taskId,
            String taskNo,
            UUID tenantId,
            UUID orderId,
            UUID prescriptionId,
            String prescriptionNo,
            String deviceCode,
            String pailNo,
            String bindOperationId,
            String startOperationId,
            String finishOperationId,
            String operator,
            Instant startedAt,
            Instant finishedAt
    ) {
        String sql = """
                insert into decoction_task (
                    id, task_no, tenant_id, order_id, prescription_id, prescription_no,
                    device_code, pail_no, task_status, bind_operation_id, start_operation_id,
                    finish_operation_id, operator, started_at, finished_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, 'DECOCTED', ?, ?, ?, ?, ?, ?, now())
                on conflict do nothing
                """;
        return jdbcTemplate.update(sql, taskId, taskNo, tenantId, orderId, prescriptionId, prescriptionNo,
                deviceCode, pailNo, bindOperationId, startOperationId, finishOperationId, operator,
                offsetDateTime(startedAt), offsetDateTime(finishedAt));
    }

    public int upsertSignedShipment(
            UUID shipmentId,
            UUID tenantId,
            UUID orderId,
            String orderNo,
            String logisticsNo,
            String logisticsCompany,
            Instant packageTime,
            Instant outboundTime,
            Instant signTime
    ) {
        String sql = """
                insert into shipment (
                    id, tenant_id, order_id, order_no, logistics_no, logistics_company,
                    logistics_status, pay_method, pkg_weight, pkg_num, package_time, outbound_time, sign_time
                ) values (?, ?, ?, ?, ?, ?, 'SIGNED', 'MANUAL', 0, 1, ?, ?, ?)
                on conflict (order_id) do update
                set logistics_company = excluded.logistics_company,
                    logistics_status = 'SIGNED',
                    package_time = coalesce(shipment.package_time, excluded.package_time),
                    outbound_time = coalesce(shipment.outbound_time, excluded.outbound_time),
                    sign_time = coalesce(shipment.sign_time, excluded.sign_time),
                    updated_at = now()
                """;
        return jdbcTemplate.update(sql, shipmentId, tenantId, orderId, orderNo, logisticsNo, logisticsCompany,
                offsetDateTime(packageTime), offsetDateTime(outboundTime), offsetDateTime(signTime));
    }

    public int upsertShippedShipment(
            UUID shipmentId,
            UUID tenantId,
            UUID orderId,
            String orderNo,
            String logisticsNo,
            String logisticsCompany,
            Instant outboundTime
    ) {
        String sql = """
                insert into shipment (
                    id, tenant_id, order_id, order_no, logistics_no, logistics_company,
                    logistics_status, pay_method, pkg_weight, pkg_num, outbound_time
                ) values (?, ?, ?, ?, ?, ?, 'SHIPPED', 'PDA', 0, 1, ?)
                on conflict (order_id) do update
                set logistics_no = coalesce(nullif(shipment.logistics_no, ''), excluded.logistics_no),
                    logistics_company = excluded.logistics_company,
                    logistics_status = 'SHIPPED',
                    outbound_time = coalesce(shipment.outbound_time, excluded.outbound_time),
                    updated_at = now()
                """;
        return jdbcTemplate.update(sql, shipmentId, tenantId, orderId, orderNo, logisticsNo, logisticsCompany,
                offsetDateTime(outboundTime));
    }

    public Optional<String> findShipmentNoByOrderId(UUID orderId) {
        String sql = """
                select logistics_no
                from shipment
                where order_id = ?
                order by created_at desc
                limit 1
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("logistics_no"), orderId)
                .stream()
                .findFirst();
    }

    public int insertShipmentTrace(
            UUID traceId,
            UUID tenantId,
            UUID orderId,
            String logisticsNo,
            String traceStatus,
            String traceContent,
            Instant traceTime
    ) {
        String sql = """
                insert into shipment_trace (
                    id, tenant_id, shipment_id, order_id, logistics_no,
                    trace_status, trace_content, raw_payload, trace_time
                )
                select ?, ?, s.id, ?, ?, ?, ?, '{}'::jsonb, ?
                from shipment s
                where s.order_id = ?
                on conflict do nothing
                """;
        return jdbcTemplate.update(sql, traceId, tenantId, orderId, logisticsNo, traceStatus, traceContent,
                offsetDateTime(traceTime), orderId);
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

    private String adminOrderWarehouseSelectSql() {
        return """
                select
                    o.id as order_id,
                    o.tenant_id,
                    o.order_no,
                    o.external_order_no,
                    o.status as order_status,
                    o.created_at,
                    o.batch_no,
                    i.institution_name,
                    i.storage_type,
                    o.address_type,
                    o.receiver_name,
                    o.receiver_phone,
                    o.delivery_time,
                    o.receiver_province,
                    o.receiver_city,
                    o.receiver_zone,
                    o.receiver_address,
                    string_agg(distinct nullif(p.hospital_type, ''), ',') as hospital_types,
                    o.patient_name,
                    null::varchar as patient_age,
                    string_agg(distinct nullif(p.department_name, ''), ',') as department_names,
                    string_agg(distinct nullif(p.prescription_type, ''), ',') as prescription_types,
                    string_agg(distinct nullif(p.prescription_no, ''), ',') as prescription_nos,
                    string_agg(distinct nullif(p.external_prescription_no, ''), ',') as external_prescription_nos,
                    string_agg(distinct p.dose_count::text, ',') filter (where p.dose_count is not null) as dose_counts,
                    string_agg(distinct p.per_pack_num::text, ',') filter (where p.per_pack_num is not null) as per_pack_nums,
                    string_agg(distinct p.per_pack_dose::text, ',') filter (where p.per_pack_dose is not null) as per_pack_doses,
                    s.logistics_company,
                    s.logistics_no
                from order_main o
                join prescription p on p.order_id = o.id
                join institution i on i.id = o.institution_id
                left join shipment s on s.order_id = o.id
                where 1 = 1
                """;
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
                rs.getObject("prescription_id", UUID.class),
                rs.getString("prescription_status"),
                rs.getString("prescription_nos"),
                rs.getString("external_prescription_nos"),
                rs.getString("prescription_types"),
                rs.getString("hospital_types"),
                rs.getInt("prescription_count"),
                rs.getInt("detail_count"),
                integer(rs, "dose_count"),
                integer(rs, "is_within"),
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

    private AdminOrderRecheckItem mapAdminOrderRecheckItem(ResultSet rs, int rowNum) throws SQLException {
        return new AdminOrderRecheckItem(
                rs.getObject("order_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getObject("prescription_id", UUID.class),
                rs.getString("institution_name"),
                rs.getString("storage_type"),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("order_status"),
                rs.getString("prescription_no"),
                rs.getString("external_prescription_no"),
                rs.getString("prescription_type"),
                rs.getString("hospital_type"),
                integer(rs, "is_within"),
                integer(rs, "dose_count"),
                rs.getString("patient_name"),
                rs.getString("patient_phone"),
                rs.getString("address_type"),
                rs.getString("batch_no"),
                instant(rs, "delivery_time"),
                instant(rs, "order_created_at"),
                instant(rs, "dispensed_at"),
                rs.getString("dispenser"),
                instant(rs, "rechecked_at"),
                rs.getString("rechecker"),
                rs.getString("pail_nos"),
                rs.getString("order_remark"),
                instant(rs, "updated_at")
        );
    }

    private AdminOrderReviewItem mapAdminOrderReviewItem(ResultSet rs, int rowNum) throws SQLException {
        return new AdminOrderReviewItem(
                rs.getObject("order_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("institution_name"),
                rs.getString("storage_type"),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("order_status"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("receiver_province"),
                rs.getString("receiver_city"),
                rs.getString("receiver_zone"),
                rs.getString("receiver_address"),
                rs.getString("address_type"),
                instant(rs, "delivery_time"),
                instant(rs, "order_created_at"),
                rs.getString("prescription_nos"),
                rs.getString("external_prescription_nos"),
                rs.getString("hospital_types"),
                rs.getString("patient_name"),
                rs.getString("patient_phone"),
                rs.getString("prescription_types"),
                rs.getString("dose_counts"),
                rs.getInt("prescription_count"),
                rs.getString("order_remark"),
                rs.getObject("review_task_id", UUID.class),
                rs.getString("review_task_status"),
                rs.getString("reviewer"),
                rs.getString("review_comment"),
                instant(rs, "task_created_at"),
                instant(rs, "task_completed_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminOperatorRecord mapAdminOperatorRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminOperatorRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("role_code"),
                rs.getBoolean("enabled"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminOperatorRoleRecord mapAdminOperatorRoleRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminOperatorRoleRecord(
                rs.getString("role_code"),
                rs.getLong("operator_count"),
                rs.getLong("enabled_count"),
                rs.getLong("disabled_count"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminDictTypeRecord mapAdminDictTypeRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminDictTypeRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("type_code"),
                rs.getString("type_name"),
                rs.getBoolean("enabled"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminDictItemRecord mapAdminDictItemRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminDictItemRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("type_id", UUID.class),
                rs.getString("type_code"),
                rs.getString("type_name"),
                rs.getString("item_code"),
                rs.getString("item_name"),
                rs.getString("item_value"),
                rs.getInt("sort_no"),
                rs.getBoolean("enabled"),
                rs.getString("remark"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminSystemConfigRecord mapAdminSystemConfigRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminSystemConfigRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("config_key"),
                rs.getString("config_name"),
                rs.getString("config_value"),
                rs.getString("value_type"),
                rs.getBoolean("enabled"),
                rs.getString("remark"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminDecoctCenterRecord mapAdminDecoctCenterRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminDecoctCenterRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("center_code"),
                rs.getString("center_name"),
                rs.getString("contact_name"),
                rs.getString("contact_phone"),
                rs.getString("address"),
                rs.getBoolean("enabled"),
                rs.getString("remark"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminHerbRecord mapAdminHerbRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminHerbRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("herb_code"),
                rs.getString("herb_name"),
                rs.getString("drug_specs"),
                rs.getString("drug_origin"),
                rs.getString("unit"),
                rs.getBigDecimal("retail_price"),
                rs.getBoolean("enabled"),
                rs.getString("remark"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminHerbAreaRecord mapAdminHerbAreaRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminHerbAreaRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("area_code"),
                rs.getString("area_name"),
                rs.getBoolean("enabled"),
                rs.getString("remark"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminHerbIndexRecord mapAdminHerbIndexRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminHerbIndexRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("institution_code"),
                rs.getString("institution_name"),
                rs.getString("external_herb_code"),
                rs.getString("external_herb_name"),
                rs.getObject("herb_id", UUID.class),
                rs.getString("herb_code"),
                rs.getString("herb_name"),
                rs.getString("match_type"),
                rs.getBoolean("enabled"),
                rs.getString("remark"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminHerbIndexOperationLogRecord mapAdminHerbIndexOperationLogRecord(
            ResultSet rs,
            int rowNum
    ) throws SQLException {
        return new AdminHerbIndexOperationLogRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("index_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("institution_code"),
                rs.getString("institution_name"),
                rs.getString("external_herb_code"),
                rs.getString("external_herb_name"),
                rs.getObject("herb_id", UUID.class),
                rs.getString("herb_code"),
                rs.getString("herb_name"),
                rs.getString("action_type"),
                rs.getString("operator"),
                rs.getString("remark"),
                instant(rs, "created_at")
        );
    }

    private AdminInstitutionRecord mapAdminInstitutionRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminInstitutionRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("institution_code"),
                rs.getString("institution_name"),
                rs.getString("institution_type"),
                rs.getString("status"),
                rs.getString("storage_type"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminInstitutionAppRecord mapAdminInstitutionAppRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminInstitutionAppRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("institution_code"),
                rs.getString("institution_name"),
                rs.getString("institution_type"),
                rs.getString("app_key"),
                rs.getString("sign_type"),
                rs.getString("callback_url"),
                rs.getBoolean("enabled"),
                rs.getBoolean("app_secret_configured"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminInstitutionApiRecord mapAdminInstitutionApiRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminInstitutionApiRecord(
                rs.getObject("id", UUID.class),
                rs.getString("api_code"),
                rs.getString("api_name"),
                rs.getString("request_method"),
                rs.getString("request_path"),
                rs.getString("description"),
                rs.getBoolean("enabled"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminInstitutionApiPermissionRecord mapAdminInstitutionApiPermissionRecord(ResultSet rs, int rowNum)
            throws SQLException {
        return new AdminInstitutionApiPermissionRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("institution_code"),
                rs.getString("institution_name"),
                rs.getString("institution_type"),
                rs.getObject("api_id", UUID.class),
                rs.getString("api_code"),
                rs.getString("api_name"),
                rs.getString("request_method"),
                rs.getString("request_path"),
                rs.getBoolean("enabled"),
                rs.getString("remark"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminInstitutionIpWhitelistRecord mapAdminInstitutionIpWhitelistRecord(ResultSet rs, int rowNum)
            throws SQLException {
        return new AdminInstitutionIpWhitelistRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("institution_code"),
                rs.getString("institution_name"),
                rs.getString("institution_type"),
                rs.getString("ip_range"),
                rs.getBoolean("enabled"),
                instant(rs, "created_at")
        );
    }

    private AdminLogisticsSpecialRuleRecord mapAdminLogisticsSpecialRuleRecord(ResultSet rs, int rowNum)
            throws SQLException {
        return new AdminLogisticsSpecialRuleRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("institution_code"),
                rs.getString("institution_name"),
                rs.getString("institution_type"),
                rs.getString("rule_name"),
                rs.getString("logistics_company"),
                rs.getBigDecimal("base_fee"),
                rs.getBigDecimal("extra_fee"),
                rs.getBigDecimal("free_threshold"),
                rs.getString("remark"),
                rs.getBoolean("enabled"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminLogisticsAddressCostRecord mapAdminLogisticsAddressCostRecord(ResultSet rs, int rowNum)
            throws SQLException {
        return new AdminLogisticsAddressCostRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("institution_code"),
                rs.getString("institution_name"),
                rs.getString("institution_type"),
                rs.getString("logistics_company"),
                rs.getString("province"),
                rs.getString("city"),
                rs.getString("district"),
                rs.getBigDecimal("cost_amount"),
                rs.getString("remark"),
                rs.getBoolean("enabled"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminOrderMergeRecord mapAdminOrderMergeRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminOrderMergeRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("merge_no"),
                rs.getString("logistics_company"),
                rs.getString("logistics_no"),
                rs.getString("status"),
                rs.getString("remark"),
                rs.getInt("order_count"),
                rs.getString("order_nos"),
                rs.getString("institution_names"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminOrderMergeCandidate mapAdminOrderMergeCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new AdminOrderMergeCandidate(
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("order_no")
        );
    }

    private AdminOrderInterceptRuleRecord mapAdminOrderInterceptRuleRecord(ResultSet rs, int rowNum)
            throws SQLException {
        return new AdminOrderInterceptRuleRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("rule_code"),
                rs.getString("rule_name"),
                rs.getString("intercept_stage"),
                rs.getString("match_field"),
                rs.getString("match_type"),
                rs.getString("match_value"),
                rs.getString("reason"),
                rs.getInt("priority"),
                rs.getBoolean("enabled"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminLabelTemplateRecord mapAdminLabelTemplateRecord(ResultSet rs, int rowNum)
            throws SQLException {
        return new AdminLabelTemplateRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("template_code"),
                rs.getString("template_name"),
                rs.getString("scope_type"),
                rs.getObject("institution_id", UUID.class),
                rs.getString("institution_name"),
                rs.getString("prescription_type"),
                rs.getInt("label_width_mm"),
                rs.getInt("label_height_mm"),
                rs.getString("content_template"),
                rs.getBoolean("enabled"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminManualProcessItem mapAdminManualProcessItem(ResultSet rs, int rowNum) throws SQLException {
        return new AdminManualProcessItem(
                rs.getObject("order_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("institution_id", UUID.class),
                rs.getString("institution_name"),
                rs.getString("storage_type"),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("order_status"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("receiver_province"),
                rs.getString("receiver_city"),
                rs.getString("receiver_zone"),
                rs.getString("receiver_address"),
                rs.getString("address_type"),
                rs.getString("patient_names"),
                rs.getString("hospital_types"),
                rs.getString("prescription_types"),
                rs.getString("prescription_nos"),
                rs.getString("external_prescription_nos"),
                rs.getString("dose_counts"),
                integer(rs, "prescription_count"),
                instant(rs, "delivery_time"),
                rs.getString("order_remark"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminOrderWarehouseItem mapAdminOrderWarehouseItem(ResultSet rs, int rowNum) throws SQLException {
        return new AdminOrderWarehouseItem(
                rs.getObject("order_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("order_status"),
                instant(rs, "created_at"),
                rs.getString("batch_no"),
                rs.getString("institution_name"),
                rs.getString("storage_type"),
                rs.getString("address_type"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                instant(rs, "delivery_time"),
                rs.getString("receiver_province"),
                rs.getString("receiver_city"),
                rs.getString("receiver_zone"),
                rs.getString("receiver_address"),
                rs.getString("hospital_types"),
                rs.getString("patient_name"),
                rs.getString("patient_age"),
                rs.getString("department_names"),
                rs.getString("prescription_types"),
                rs.getString("prescription_nos"),
                rs.getString("external_prescription_nos"),
                rs.getString("dose_counts"),
                rs.getString("per_pack_nums"),
                rs.getString("per_pack_doses"),
                rs.getString("logistics_company"),
                rs.getString("logistics_no")
        );
    }

    private AdminOrderReceiptItem mapAdminOrderReceiptItem(ResultSet rs, int rowNum) throws SQLException {
        return new AdminOrderReceiptItem(
                rs.getObject("order_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("institution_name"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("receiver_province"),
                rs.getString("receiver_city"),
                rs.getString("receiver_zone"),
                rs.getString("receiver_address"),
                rs.getString("patient_name"),
                rs.getString("prescription_types"),
                rs.getString("order_status"),
                rs.getString("logistics_company"),
                rs.getString("logistics_no"),
                rs.getString("logistics_status"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private AdminPrescriptionReprintItem mapAdminPrescriptionReprintItem(ResultSet rs, int rowNum)
            throws SQLException {
        return new AdminPrescriptionReprintItem(
                rs.getObject("order_id", UUID.class),
                rs.getObject("prescription_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("order_status"),
                rs.getString("prescription_no"),
                rs.getString("external_prescription_no"),
                rs.getString("prescription_status"),
                rs.getString("institution_name"),
                rs.getString("patient_name"),
                rs.getString("patient_phone"),
                rs.getString("receiver_province"),
                rs.getString("receiver_city"),
                rs.getString("receiver_zone"),
                rs.getString("receiver_address"),
                rs.getString("address_type"),
                instant(rs, "delivery_time"),
                instant(rs, "created_at"),
                rs.getString("hospital_type"),
                rs.getString("prescription_type"),
                integer(rs, "is_within"),
                integer(rs, "dose_count"),
                rs.getString("batch_no"),
                rs.getString("dispenser")
        );
    }

    private AdminLabelPrintRecord mapAdminLabelPrintRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AdminLabelPrintRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getObject("prescription_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("prescription_no"),
                rs.getString("external_prescription_no"),
                rs.getString("institution_name"),
                rs.getString("patient_name"),
                rs.getString("print_status"),
                rs.getString("print_channel"),
                rs.getString("printer_code"),
                rs.getString("printer_name"),
                rs.getString("provider"),
                rs.getString("provider_task_no"),
                rs.getObject("template_id", UUID.class),
                rs.getString("template_name"),
                rs.getString("request_param"),
                rs.getString("response_body"),
                rs.getString("failure_reason"),
                rs.getString("operator"),
                rs.getObject("retry_of", UUID.class),
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
                rs.getBigDecimal("logistics_fee"),
                rs.getBigDecimal("discount_amount"),
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
                integer(rs, "boil_times"),
                integer(rs, "is_within"),
                integer(rs, "per_pack_num"),
                integer(rs, "per_pack_dose"),
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
            BigDecimal logisticsFee,
            BigDecimal discountAmount,
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

        private void addNotEqualsFilter(String column, String value) {
            String trimmed = trimmed(value);
            if (trimmed != null) {
                append(" and " + column + " <> ?");
                add(trimmed);
            }
        }

        private void addInFilter(String column, List<String> values) {
            if (values == null || values.isEmpty()) {
                return;
            }
            append(" and " + column + " in (" + "?,".repeat(values.size() - 1) + "?)");
            args.addAll(values);
        }

        private void addExistsShipmentLike(String column, String value) {
            String trimmed = trimmed(value);
            if (trimmed != null) {
                append(" and exists (select 1 from shipment s where s.order_id = o.id and " + column + " ilike ?)");
                add("%" + trimmed + "%");
            }
        }

        private void addReceiptPrescriptionFilter(String value) {
            String trimmed = trimmed(value);
            if (trimmed != null) {
                append("""
                         and exists (
                            select 1
                            from prescription p
                            where p.order_id = o.id
                              and (p.prescription_no ilike ? or p.external_prescription_no ilike ?)
                        )
                        """);
                String pattern = "%" + trimmed + "%";
                add(pattern);
                add(pattern);
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
