package com.zhyf.report.infrastructure;

import com.zhyf.report.application.ReportRecords;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReportQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReportRecords.ReportOverview loadOverview(Instant from, Instant to, int trendDays) {
        return new ReportRecords.ReportOverview(
                from,
                to,
                trendDays,
                countRows("order_main", "created_at", from, to),
                countRows("prescription", "created_at", from, to),
                countRows("shipment", "created_at", from, to),
                countRows("callback_record", "created_at", from, to),
                countRowsByStatus("portal_address_supplement", "supplement_status", "PENDING", "created_at", from, to),
                statusCounts("order_main", "status", "created_at", from, to),
                statusCounts("callback_record", "status", "created_at", from, to),
                dailyOrderCounts(trendDays)
        );
    }

    public List<ReportRecords.InstitutionPrescriptionCount> loadInstitutionPrescriptionCounts(Instant from, Instant to) {
        QueryParts query = new QueryParts("""
                select i.id::text as institution_id,
                       i.institution_code,
                       i.institution_name,
                       count(distinct o.id) as order_count,
                       count(p.id) as prescription_count,
                       coalesce(sum(p.dose_count), 0) as dose_count,
                       coalesce(sum(p.total_amount), 0) as total_amount
                from prescription p
                join institution i on i.id = p.institution_id
                join order_main o on o.id = p.order_id
                where 1 = 1
                """);
        query.addRangeFilter("p.created_at", from, to);
        query.append("""
                 group by i.id, i.institution_code, i.institution_name
                 order by prescription_count desc, i.institution_name asc
                """);
        return jdbcTemplate.query(query.sql(), this::mapInstitutionPrescriptionCount, query.args());
    }

    public List<ReportRecords.DispensePerformance> loadDispensePerformance(Instant from, Instant to) {
        QueryParts query = new QueryParts("""
                select d.dispenser,
                       count(d.id) as dispense_count,
                       count(distinct d.order_id) as order_count,
                       coalesce(sum(order_prescriptions.prescription_count), 0) as prescription_count,
                       coalesce(sum(order_prescriptions.dose_count), 0) as dose_count,
                       min(d.dispensed_at) as first_dispensed_at,
                       max(d.dispensed_at) as last_dispensed_at
                from dispense_record d
                join lateral (
                    select count(p.id) as prescription_count,
                           coalesce(sum(p.dose_count), 0) as dose_count
                    from prescription p
                    where p.order_id = d.order_id
                ) order_prescriptions on true
                where 1 = 1
                """);
        query.addRangeFilter("d.dispensed_at", from, to);
        query.append("""
                 group by d.dispenser
                 order by dispense_count desc, d.dispenser asc
                """);
        return jdbcTemplate.query(query.sql(), this::mapDispensePerformance, query.args());
    }

    public List<ReportRecords.RecheckPerformance> loadRecheckPerformance(Instant from, Instant to) {
        QueryParts query = new QueryParts("""
                select t.assigned_to as rechecker,
                       count(t.id) as recheck_count,
                       count(distinct t.order_id) as order_count,
                       coalesce(sum(order_prescriptions.prescription_count), 0) as prescription_count,
                       coalesce(sum(order_prescriptions.dose_count), 0) as dose_count,
                       min(t.completed_at) as first_rechecked_at,
                       max(t.completed_at) as last_rechecked_at
                from workflow_task t
                join lateral (
                    select count(p.id) as prescription_count,
                           coalesce(sum(p.dose_count), 0) as dose_count
                    from prescription p
                    where p.order_id = t.order_id
                ) order_prescriptions on true
                where t.task_type = 'PRESCRIPTION_RECHECK'
                  and t.task_status = 'COMPLETED'
                  and t.completed_at is not null
                  and nullif(t.assigned_to, '') is not null
                """);
        query.addRangeFilter("t.completed_at", from, to);
        query.append("""
                 group by t.assigned_to
                 order by recheck_count desc, t.assigned_to asc
                """);
        return jdbcTemplate.query(query.sql(), this::mapRecheckPerformance, query.args());
    }

    public List<ReportRecords.AuditPerformance> loadAuditPerformance(Instant from, Instant to) {
        QueryParts query = new QueryParts("""
                select t.assigned_to as auditor,
                       count(t.id) as audit_count,
                       count(t.id) filter (where t.task_status = 'APPROVED') as approved_count,
                       count(t.id) filter (where t.task_status = 'REJECTED') as rejected_count,
                       count(distinct t.order_id) as order_count,
                       coalesce(sum(order_prescriptions.prescription_count), 0) as prescription_count,
                       coalesce(sum(order_prescriptions.dose_count), 0) as dose_count,
                       min(t.completed_at) as first_audited_at,
                       max(t.completed_at) as last_audited_at
                from workflow_task t
                join lateral (
                    select count(p.id) as prescription_count,
                           coalesce(sum(p.dose_count), 0) as dose_count
                    from prescription p
                    where p.order_id = t.order_id
                ) order_prescriptions on true
                where t.task_type = 'ORDER_REVIEW'
                  and t.task_status in ('APPROVED', 'REJECTED')
                  and t.completed_at is not null
                  and nullif(t.assigned_to, '') is not null
                """);
        query.addRangeFilter("t.completed_at", from, to);
        query.append("""
                 group by t.assigned_to
                 order by audit_count desc, t.assigned_to asc
                """);
        return jdbcTemplate.query(query.sql(), this::mapAuditPerformance, query.args());
    }

    public List<ReportRecords.DecoctionPerformance> loadDecoctionPerformance(Instant from, Instant to) {
        QueryParts query = new QueryParts("""
                select r.operator as decoction_operator,
                       count(r.id) as decoction_count,
                       count(distinct r.order_id) as order_count,
                       count(distinct r.task_id) as prescription_count,
                       coalesce(sum(p.dose_count), 0) as dose_count,
                       count(distinct r.device_code) as device_count,
                       min(r.action_time) as first_finished_at,
                       max(r.action_time) as last_finished_at
                from decoction_device_work_record r
                left join prescription p on p.order_id = r.order_id
                    and p.prescription_no = r.prescription_no
                where r.action_type = 'FINISH'
                  and r.action_result = 'ACCEPTED'
                  and nullif(r.operator, '') is not null
                """);
        query.addRangeFilter("r.action_time", from, to);
        query.append("""
                 group by r.operator
                 order by decoction_count desc, r.operator asc
                """);
        return jdbcTemplate.query(query.sql(), this::mapDecoctionPerformance, query.args());
    }

    public List<ReportRecords.HerbDosage> loadHerbDosage(Instant from, Instant to) {
        QueryParts query = new QueryParts("""
                select coalesce(nullif(d.platform_drug_code, ''), nullif(d.drug_code, ''), '-') as herb_code,
                       coalesce(nullif(d.platform_drug_name, ''), nullif(d.drug_name, ''), '未命名药材') as herb_name,
                       d.drug_specs,
                       d.drug_origin,
                       d.unit,
                       count(d.id) as detail_count,
                       count(distinct p.id) as prescription_count,
                       count(distinct p.order_id) as order_count,
                       coalesce(sum(d.quantity), 0) as total_quantity,
                       coalesce(sum(d.total_price), 0) as total_amount,
                       coalesce(sum(d.settlement_total_price), 0) as settlement_amount
                from prescription_detail d
                join prescription p on p.id = d.prescription_id
                where 1 = 1
                """);
        query.addRangeFilter("p.created_at", from, to);
        query.append("""
                  and (
                      nullif(d.platform_drug_code, '') is not null
                      or nullif(d.drug_code, '') is not null
                      or nullif(d.platform_drug_name, '') is not null
                      or nullif(d.drug_name, '') is not null
                  )
                 group by coalesce(nullif(d.platform_drug_code, ''), nullif(d.drug_code, ''), '-'),
                          coalesce(nullif(d.platform_drug_name, ''), nullif(d.drug_name, ''), '未命名药材'),
                          d.drug_specs,
                          d.drug_origin,
                          d.unit
                 order by total_quantity desc, herb_name asc
                """);
        return jdbcTemplate.query(query.sql(), this::mapHerbDosage, query.args());
    }

    public List<ReportRecords.InstitutionHerbReconciliation> loadInstitutionHerbReconciliation(Instant from, Instant to) {
        QueryParts query = new QueryParts("""
                select i.id::text as institution_id,
                       i.institution_code,
                       i.institution_name,
                       coalesce(nullif(d.platform_drug_code, ''), nullif(d.drug_code, ''), '-') as herb_code,
                       coalesce(nullif(d.platform_drug_name, ''), nullif(d.drug_name, ''), '未命名药材') as herb_name,
                       d.drug_specs,
                       d.drug_origin,
                       d.unit,
                       count(d.id) as detail_count,
                       count(distinct p.id) as prescription_count,
                       count(distinct p.order_id) as order_count,
                       coalesce(sum(d.quantity), 0) as total_quantity,
                       coalesce(sum(d.total_price), 0) as total_amount,
                       coalesce(sum(d.settlement_total_price), 0) as settlement_amount
                from prescription_detail d
                join prescription p on p.id = d.prescription_id
                join institution i on i.id = p.institution_id
                where 1 = 1
                """);
        query.addRangeFilter("p.created_at", from, to);
        query.append("""
                  and (
                      nullif(d.platform_drug_code, '') is not null
                      or nullif(d.drug_code, '') is not null
                      or nullif(d.platform_drug_name, '') is not null
                      or nullif(d.drug_name, '') is not null
                  )
                 group by i.id,
                          i.institution_code,
                          i.institution_name,
                          coalesce(nullif(d.platform_drug_code, ''), nullif(d.drug_code, ''), '-'),
                          coalesce(nullif(d.platform_drug_name, ''), nullif(d.drug_name, ''), '未命名药材'),
                          d.drug_specs,
                          d.drug_origin,
                          d.unit
                 order by i.institution_name asc, total_quantity desc, herb_name asc
                """);
        return jdbcTemplate.query(query.sql(), this::mapInstitutionHerbReconciliation, query.args());
    }

    public List<ReportRecords.PrescriptionHerbDetail> loadPrescriptionHerbDetails(Instant from, Instant to) {
        QueryParts query = new QueryParts("""
                select i.institution_code,
                       i.institution_name,
                       o.order_no,
                       o.external_order_no,
                       p.prescription_no,
                       p.external_prescription_no,
                       coalesce(nullif(d.platform_drug_code, ''), nullif(d.drug_code, ''), '-') as herb_code,
                       coalesce(nullif(d.platform_drug_name, ''), nullif(d.drug_name, ''), '未命名药材') as herb_name,
                       d.drug_specs,
                       d.drug_origin,
                       d.dose,
                       d.unit,
                       d.special_usage,
                       d.quantity,
                       d.unit_price,
                       d.total_price,
                       d.settlement_unit_price,
                       d.settlement_total_price,
                       d.batch_no,
                       d.remark,
                       p.created_at as prescription_created_at
                from prescription_detail d
                join prescription p on p.id = d.prescription_id
                join order_main o on o.id = p.order_id
                join institution i on i.id = p.institution_id
                where 1 = 1
                """);
        query.addRangeFilter("p.created_at", from, to);
        query.append("""
                  and (
                      nullif(d.platform_drug_code, '') is not null
                      or nullif(d.drug_code, '') is not null
                      or nullif(d.platform_drug_name, '') is not null
                      or nullif(d.drug_name, '') is not null
                  )
                 order by p.created_at desc, o.order_no desc, p.prescription_no asc, d.sort_no asc
                """);
        return jdbcTemplate.query(query.sql(), this::mapPrescriptionHerbDetail, query.args());
    }

    public List<ReportRecords.AuditPerformanceDetail> loadAuditPerformanceDetails(Instant from, Instant to) {
        QueryParts query = new QueryParts("""
                select t.assigned_to as auditor,
                       t.task_status as audit_result,
                       o.order_no,
                       o.external_order_no,
                       i.institution_name,
                       o.patient_name,
                       order_prescriptions.prescription_count,
                       order_prescriptions.dose_count,
                       t.review_comment,
                       t.completed_at as audited_at
                from workflow_task t
                join order_main o on o.id = t.order_id
                join institution i on i.id = o.institution_id
                join lateral (
                    select count(p.id) as prescription_count,
                           coalesce(sum(p.dose_count), 0) as dose_count
                    from prescription p
                    where p.order_id = t.order_id
                ) order_prescriptions on true
                where t.task_type = 'ORDER_REVIEW'
                  and t.task_status in ('APPROVED', 'REJECTED')
                  and t.completed_at is not null
                  and nullif(t.assigned_to, '') is not null
                """);
        query.addRangeFilter("t.completed_at", from, to);
        query.append("""
                 order by t.completed_at desc, t.assigned_to asc, o.order_no desc
                """);
        return jdbcTemplate.query(query.sql(), this::mapAuditPerformanceDetail, query.args());
    }

    public List<ReportRecords.DispensePerformanceDetail> loadDispensePerformanceDetails(Instant from, Instant to) {
        QueryParts query = new QueryParts("""
                select d.dispenser,
                       o.order_no,
                       o.external_order_no,
                       i.institution_name,
                       o.patient_name,
                       order_prescriptions.prescription_count,
                       order_prescriptions.dose_count,
                       d.print_status,
                       d.dispense_comment,
                       d.dispensed_at
                from dispense_record d
                join order_main o on o.id = d.order_id
                join institution i on i.id = o.institution_id
                join lateral (
                    select count(p.id) as prescription_count,
                           coalesce(sum(p.dose_count), 0) as dose_count
                    from prescription p
                    where p.order_id = d.order_id
                ) order_prescriptions on true
                where d.dispensed_at is not null
                  and nullif(d.dispenser, '') is not null
                """);
        query.addRangeFilter("d.dispensed_at", from, to);
        query.append("""
                 order by d.dispensed_at desc, d.dispenser asc, o.order_no desc
                """);
        return jdbcTemplate.query(query.sql(), this::mapDispensePerformanceDetail, query.args());
    }

    private long countRows(String table, String timeColumn, Instant from, Instant to) {
        QueryParts query = new QueryParts("select count(*) from " + table + " where 1 = 1");
        query.addRangeFilter(timeColumn, from, to);
        Long value = jdbcTemplate.queryForObject(query.sql(), Long.class, query.args());
        return value == null ? 0 : value;
    }

    private long countRowsByStatus(
            String table,
            String statusColumn,
            String status,
            String timeColumn,
            Instant from,
            Instant to
    ) {
        QueryParts query = new QueryParts("select count(*) from " + table + " where " + statusColumn + " = ?");
        query.add(status);
        query.addRangeFilter(timeColumn, from, to);
        Long value = jdbcTemplate.queryForObject(query.sql(), Long.class, query.args());
        return value == null ? 0 : value;
    }

    private List<ReportRecords.StatusCount> statusCounts(
            String table,
            String statusColumn,
            String timeColumn,
            Instant from,
            Instant to
    ) {
        QueryParts query = new QueryParts("""
                select %s as item_status, count(*) as item_count
                from %s
                where 1 = 1
                """.formatted(statusColumn, table));
        query.addRangeFilter(timeColumn, from, to);
        query.append(" group by " + statusColumn + " order by item_count desc, item_status asc");
        return jdbcTemplate.query(query.sql(), this::mapStatusCount, query.args());
    }

    private List<ReportRecords.DailyOrderCount> dailyOrderCounts(int trendDays) {
        String sql = """
                select created_at::date as day, count(*) as order_count
                from order_main
                where created_at >= current_date - (? * interval '1 day')
                group by created_at::date
                order by day asc
                """;
        return jdbcTemplate.query(sql, this::mapDailyOrderCount, trendDays - 1);
    }

    private ReportRecords.StatusCount mapStatusCount(ResultSet rs, int rowNum) throws SQLException {
        return new ReportRecords.StatusCount(rs.getString("item_status"), rs.getLong("item_count"));
    }

    private ReportRecords.DailyOrderCount mapDailyOrderCount(ResultSet rs, int rowNum) throws SQLException {
        return new ReportRecords.DailyOrderCount(rs.getObject("day", java.time.LocalDate.class), rs.getLong("order_count"));
    }

    private ReportRecords.InstitutionPrescriptionCount mapInstitutionPrescriptionCount(ResultSet rs, int rowNum)
            throws SQLException {
        return new ReportRecords.InstitutionPrescriptionCount(
                rs.getString("institution_id"),
                rs.getString("institution_code"),
                rs.getString("institution_name"),
                rs.getLong("order_count"),
                rs.getLong("prescription_count"),
                rs.getLong("dose_count"),
                rs.getBigDecimal("total_amount")
        );
    }

    private ReportRecords.DispensePerformance mapDispensePerformance(ResultSet rs, int rowNum) throws SQLException {
        return new ReportRecords.DispensePerformance(
                rs.getString("dispenser"),
                rs.getLong("dispense_count"),
                rs.getLong("order_count"),
                rs.getLong("prescription_count"),
                rs.getLong("dose_count"),
                instant(rs, "first_dispensed_at"),
                instant(rs, "last_dispensed_at")
        );
    }

    private ReportRecords.RecheckPerformance mapRecheckPerformance(ResultSet rs, int rowNum) throws SQLException {
        return new ReportRecords.RecheckPerformance(
                rs.getString("rechecker"),
                rs.getLong("recheck_count"),
                rs.getLong("order_count"),
                rs.getLong("prescription_count"),
                rs.getLong("dose_count"),
                instant(rs, "first_rechecked_at"),
                instant(rs, "last_rechecked_at")
        );
    }

    private ReportRecords.AuditPerformance mapAuditPerformance(ResultSet rs, int rowNum) throws SQLException {
        return new ReportRecords.AuditPerformance(
                rs.getString("auditor"),
                rs.getLong("audit_count"),
                rs.getLong("approved_count"),
                rs.getLong("rejected_count"),
                rs.getLong("order_count"),
                rs.getLong("prescription_count"),
                rs.getLong("dose_count"),
                instant(rs, "first_audited_at"),
                instant(rs, "last_audited_at")
        );
    }

    private ReportRecords.DecoctionPerformance mapDecoctionPerformance(ResultSet rs, int rowNum) throws SQLException {
        return new ReportRecords.DecoctionPerformance(
                rs.getString("decoction_operator"),
                rs.getLong("decoction_count"),
                rs.getLong("order_count"),
                rs.getLong("prescription_count"),
                rs.getLong("dose_count"),
                rs.getLong("device_count"),
                instant(rs, "first_finished_at"),
                instant(rs, "last_finished_at")
        );
    }

    private ReportRecords.HerbDosage mapHerbDosage(ResultSet rs, int rowNum) throws SQLException {
        return new ReportRecords.HerbDosage(
                rs.getString("herb_code"),
                rs.getString("herb_name"),
                rs.getString("drug_specs"),
                rs.getString("drug_origin"),
                rs.getString("unit"),
                rs.getLong("detail_count"),
                rs.getLong("prescription_count"),
                rs.getLong("order_count"),
                rs.getBigDecimal("total_quantity"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("settlement_amount")
        );
    }

    private ReportRecords.InstitutionHerbReconciliation mapInstitutionHerbReconciliation(ResultSet rs, int rowNum)
            throws SQLException {
        return new ReportRecords.InstitutionHerbReconciliation(
                rs.getString("institution_id"),
                rs.getString("institution_code"),
                rs.getString("institution_name"),
                rs.getString("herb_code"),
                rs.getString("herb_name"),
                rs.getString("drug_specs"),
                rs.getString("drug_origin"),
                rs.getString("unit"),
                rs.getLong("detail_count"),
                rs.getLong("prescription_count"),
                rs.getLong("order_count"),
                rs.getBigDecimal("total_quantity"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("settlement_amount")
        );
    }

    private ReportRecords.PrescriptionHerbDetail mapPrescriptionHerbDetail(ResultSet rs, int rowNum)
            throws SQLException {
        return new ReportRecords.PrescriptionHerbDetail(
                rs.getString("institution_code"),
                rs.getString("institution_name"),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("prescription_no"),
                rs.getString("external_prescription_no"),
                rs.getString("herb_code"),
                rs.getString("herb_name"),
                rs.getString("drug_specs"),
                rs.getString("drug_origin"),
                rs.getString("dose"),
                rs.getString("unit"),
                rs.getString("special_usage"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("total_price"),
                rs.getBigDecimal("settlement_unit_price"),
                rs.getBigDecimal("settlement_total_price"),
                rs.getString("batch_no"),
                rs.getString("remark"),
                instant(rs, "prescription_created_at")
        );
    }

    private ReportRecords.AuditPerformanceDetail mapAuditPerformanceDetail(ResultSet rs, int rowNum)
            throws SQLException {
        return new ReportRecords.AuditPerformanceDetail(
                rs.getString("auditor"),
                rs.getString("audit_result"),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("institution_name"),
                rs.getString("patient_name"),
                rs.getLong("prescription_count"),
                rs.getLong("dose_count"),
                rs.getString("review_comment"),
                instant(rs, "audited_at")
        );
    }

    private ReportRecords.DispensePerformanceDetail mapDispensePerformanceDetail(ResultSet rs, int rowNum)
            throws SQLException {
        return new ReportRecords.DispensePerformanceDetail(
                rs.getString("dispenser"),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("institution_name"),
                rs.getString("patient_name"),
                rs.getLong("prescription_count"),
                rs.getLong("dose_count"),
                rs.getString("print_status"),
                rs.getString("dispense_comment"),
                instant(rs, "dispensed_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
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
                append(" and " + column + " < ?");
                add(offsetDateTime(to));
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
