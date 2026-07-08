package com.zhyf.logistics.infrastructure;

import com.zhyf.logistics.application.LogisticsRecords;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class LogisticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public LogisticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LogisticsRecords.DeliveryOrderRecord> findDecoctedOrders(int limit) {
        String sql = """
                select tenant_id, id as order_id, order_no, external_order_no, status as order_status,
                       receiver_name, receiver_phone, receiver_address
                from order_main o
                where o.status = 'DECOCTED'
                  and not exists (select 1 from shipment s where s.order_id = o.id)
                order by o.created_at asc
                limit ?
                """;
        return jdbcTemplate.query(sql, this::mapDeliveryOrder, limit);
    }

    public Optional<LogisticsRecords.DeliveryOrderRecord> findOrderByOrderNo(String orderNo) {
        String sql = """
                select tenant_id, id as order_id, order_no, external_order_no, status as order_status,
                       receiver_name, receiver_phone, receiver_address
                from order_main
                where order_no = ?
                """;
        return jdbcTemplate.query(sql, this::mapDeliveryOrder, orderNo).stream().findFirst();
    }

    public Optional<LogisticsRecords.ShipmentRecord> findShipmentByOrderId(UUID orderId) {
        String sql = baseShipmentQuery() + " where order_id = ?";
        return jdbcTemplate.query(sql, this::mapShipment, orderId).stream().findFirst();
    }

    public Optional<LogisticsRecords.ShipmentRecord> findShipmentById(UUID shipmentId) {
        String sql = baseShipmentQuery() + " where id = ?";
        return jdbcTemplate.query(sql, this::mapShipment, shipmentId).stream().findFirst();
    }

    public Optional<LogisticsRecords.ShipmentRecord> findShipmentByLogisticsNo(String logisticsNo) {
        String sql = baseShipmentQuery() + " where logistics_no = ?";
        return jdbcTemplate.query(sql, this::mapShipment, logisticsNo).stream().findFirst();
    }

    public List<LogisticsRecords.ShipmentRecord> findShipments(String status, String orderNo, int limit) {
        QueryParts query = new QueryParts(baseShipmentQuery() + " where 1 = 1");
        query.addTextFilter("logistics_status", status);
        query.addTextFilter("order_no", orderNo);
        query.append(" order by created_at desc limit ?");
        query.add(limit);
        return jdbcTemplate.query(query.sql(), this::mapShipment, query.args());
    }

    public int createShipment(
            UUID shipmentId,
            LogisticsRecords.DeliveryOrderRecord order,
            String logisticsNo,
            String logisticsCompany,
            String payMethod,
            BigDecimal pkgWeight,
            Integer pkgNum,
            Instant packageTime
    ) {
        String sql = """
                insert into shipment (
                    id, tenant_id, order_id, order_no, logistics_no, logistics_company,
                    logistics_status, pay_method, pkg_weight, pkg_num, package_time
                ) values (?, ?, ?, ?, ?, ?, 'PACKED', ?, ?, ?, ?)
                """;
        return jdbcTemplate.update(
                sql,
                shipmentId,
                order.tenantId(),
                order.orderId(),
                order.orderNo(),
                logisticsNo,
                logisticsCompany,
                payMethod,
                pkgWeight,
                pkgNum,
                offsetDateTime(packageTime)
        );
    }

    public int updateShipmentStatus(UUID shipmentId, String status, Instant actionTime) {
        String timeColumn = switch (status) {
            case "SHIPPED", "IN_TRANSIT" -> "outbound_time";
            case "SIGNED" -> "sign_time";
            default -> null;
        };
        String sql;
        if (timeColumn == null) {
            sql = "update shipment set logistics_status = ?, updated_at = now() where id = ?";
            return jdbcTemplate.update(sql, status, shipmentId);
        }
        sql = "update shipment set logistics_status = ?, " + timeColumn + " = coalesce(" + timeColumn
                + ", ?), updated_at = now() where id = ?";
        return jdbcTemplate.update(sql, status, offsetDateTime(actionTime), shipmentId);
    }

    public int createTrace(
            UUID traceId,
            LogisticsRecords.ShipmentRecord shipment,
            String traceStatus,
            String traceContent,
            String rawPayload,
            Instant traceTime
    ) {
        String sql = """
                insert into shipment_trace (
                    id, tenant_id, shipment_id, order_id, logistics_no,
                    trace_status, trace_content, raw_payload, trace_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """;
        return jdbcTemplate.update(
                sql,
                traceId,
                shipment.tenantId(),
                shipment.shipmentId(),
                shipment.orderId(),
                shipment.logisticsNo(),
                traceStatus,
                traceContent,
                rawPayload == null ? "{}" : rawPayload,
                offsetDateTime(traceTime)
        );
    }

    public List<LogisticsRecords.ShipmentTraceRecord> findTraces(UUID shipmentId) {
        String sql = """
                select id as trace_id, tenant_id, shipment_id, order_id, logistics_no,
                       trace_status, trace_content, raw_payload::text as raw_payload, trace_time, created_at
                from shipment_trace
                where shipment_id = ?
                order by created_at desc
                """;
        return jdbcTemplate.query(sql, this::mapTrace, shipmentId);
    }

    private String baseShipmentQuery() {
        return """
                select id as shipment_id, tenant_id, order_id, order_no, logistics_no,
                       logistics_company, logistics_status, pay_method, pkg_weight, pkg_num,
                       package_time, outbound_time, sign_time, created_at, updated_at
                from shipment
                """;
    }

    private LogisticsRecords.DeliveryOrderRecord mapDeliveryOrder(ResultSet rs, int rowNum) throws SQLException {
        return new LogisticsRecords.DeliveryOrderRecord(
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("order_status"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("receiver_address")
        );
    }

    private LogisticsRecords.ShipmentRecord mapShipment(ResultSet rs, int rowNum) throws SQLException {
        return new LogisticsRecords.ShipmentRecord(
                rs.getObject("shipment_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("logistics_no"),
                rs.getString("logistics_company"),
                rs.getString("logistics_status"),
                rs.getString("pay_method"),
                rs.getBigDecimal("pkg_weight"),
                (Integer) rs.getObject("pkg_num"),
                instant(rs, "package_time"),
                instant(rs, "outbound_time"),
                instant(rs, "sign_time"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private LogisticsRecords.ShipmentTraceRecord mapTrace(ResultSet rs, int rowNum) throws SQLException {
        return new LogisticsRecords.ShipmentTraceRecord(
                rs.getObject("trace_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("shipment_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("logistics_no"),
                rs.getString("trace_status"),
                rs.getString("trace_content"),
                rs.getString("raw_payload"),
                instant(rs, "trace_time"),
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

    private static final class QueryParts {
        private final StringBuilder sql;
        private final List<Object> args = new ArrayList<>();

        private QueryParts(String baseSql) {
            this.sql = new StringBuilder(baseSql);
        }

        private void addTextFilter(String column, String value) {
            if (StringUtils.hasText(value)) {
                append(" and " + column + " = ?");
                add(value);
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
