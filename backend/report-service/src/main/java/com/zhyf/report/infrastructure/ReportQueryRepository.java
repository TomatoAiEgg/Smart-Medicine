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
