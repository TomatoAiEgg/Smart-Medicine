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
