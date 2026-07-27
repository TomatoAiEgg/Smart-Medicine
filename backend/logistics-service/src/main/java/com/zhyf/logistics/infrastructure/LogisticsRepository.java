package com.zhyf.logistics.infrastructure;

import com.zhyf.logistics.application.LogisticsRecords;
import com.zhyf.logistics.application.LogisticsShipmentQuery;
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

    public List<LogisticsRecords.DeliveryOrderRecord> findDecoctedOrders(LogisticsShipmentQuery query) {
        QueryParts parts = new QueryParts(baseReadyOrderQuery() + " where o.status = 'DECOCTED'");
        parts.append(" and not exists (select 1 from shipment s where s.order_id = o.id)");
        addOrderFilters(parts, query);
        parts.append(" order by o.created_at asc limit ?");
        parts.add(query.limit());
        return jdbcTemplate.query(parts.sql(), this::mapDeliveryOrder, parts.args());
    }

    public Optional<LogisticsRecords.DeliveryOrderRecord> findOrderByOrderNo(String orderNo) {
        String sql = baseReadyOrderQuery() + " where o.order_no = ?";
        return jdbcTemplate.query(sql, this::mapDeliveryOrder, orderNo).stream().findFirst();
    }

    public Optional<LogisticsRecords.ShipmentRecord> findShipmentByOrderId(UUID orderId) {
        String sql = baseShipmentQuery() + " where s.order_id = ?";
        return jdbcTemplate.query(sql, this::mapShipment, orderId).stream().findFirst();
    }

    public Optional<LogisticsRecords.ShipmentRecord> findShipmentById(UUID shipmentId) {
        String sql = baseShipmentQuery() + " where s.id = ?";
        return jdbcTemplate.query(sql, this::mapShipment, shipmentId).stream().findFirst();
    }

    public Optional<LogisticsRecords.ShipmentRecord> findShipmentByLogisticsNo(String logisticsNo) {
        String sql = baseShipmentQuery() + " where s.logistics_no = ?";
        return jdbcTemplate.query(sql, this::mapShipment, logisticsNo).stream().findFirst();
    }

    public List<LogisticsRecords.ShipmentRecord> findShipments(LogisticsShipmentQuery params) {
        QueryParts parts = new QueryParts(baseShipmentQuery() + " where 1 = 1");
        parts.addTextFilter("s.logistics_status", params.status());
        parts.addLikeFilter("s.logistics_company", params.logisticsCompany());
        parts.addLikeFilter("s.logistics_no", params.logisticsNo());
        addOrderFilters(parts, params);
        parts.append(" order by s.created_at desc limit ?");
        parts.add(params.limit());
        return jdbcTemplate.query(parts.sql(), this::mapShipment, parts.args());
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

    public List<LogisticsRecords.LogisticsInfoRecord> findLogisticsInfos(LogisticsShipmentQuery params) {
        QueryParts parts = new QueryParts("""
                select t.id as trace_id,
                       t.tenant_id,
                       t.shipment_id,
                       t.order_id,
                       o.order_no,
                       o.external_order_no,
                       t.logistics_no,
                       s.logistics_company,
                       coalesce(nullif(t.trace_content, ''), t.trace_status) as operation_info,
                       t.trace_status,
                       o.receiver_phone,
                       t.trace_time,
                       t.created_at
                from shipment_trace t
                join shipment s on s.id = t.shipment_id
                join order_main o on o.id = t.order_id
                where 1 = 1
                """);
        parts.addRangeFilter("t.created_at", params.startTime(), params.endTime());
        parts.addKeywordFilter(params.orderNo());
        parts.addLikeFilter("o.receiver_phone", params.receiverPhone());
        parts.addLikeFilter("t.logistics_no", params.logisticsNo());
        parts.append(" order by t.trace_time desc, t.created_at desc limit ?");
        parts.add(params.limit());
        return jdbcTemplate.query(parts.sql(), this::mapLogisticsInfo, parts.args());
    }

    private String baseShipmentQuery() {
        return """
                select s.id as shipment_id,
                       s.tenant_id,
                       s.order_id,
                       s.order_no,
                       o.external_order_no,
                       o.created_at as order_created_at,
                       i.institution_name,
                       o.patient_name,
                       o.receiver_name,
                       o.receiver_phone,
                       o.receiver_address,
                       o.address_type,
                       o.delivery_time,
                       ps.hospital_types,
                       s.logistics_no,
                       s.logistics_company,
                       s.logistics_status,
                       s.pay_method,
                       s.pkg_weight,
                       s.pkg_num,
                       s.package_time,
                       s.outbound_time,
                       s.sign_time,
                       s.created_at,
                       s.updated_at
                from shipment s
                join order_main o on o.id = s.order_id
                join institution i on i.id = o.institution_id
                left join lateral (
                    select string_agg(distinct p.hospital_type, ', ' order by p.hospital_type) as hospital_types
                    from prescription p
                    where p.order_id = o.id
                      and p.hospital_type is not null
                      and p.hospital_type <> ''
                ) ps on true
                """;
    }

    private String baseReadyOrderQuery() {
        return """
                select o.tenant_id,
                       o.id as order_id,
                       o.order_no,
                       o.external_order_no,
                       o.status as order_status,
                       i.institution_name,
                       o.patient_name,
                       o.receiver_name,
                       o.receiver_phone,
                       o.receiver_address,
                       o.address_type,
                       o.delivery_time,
                       o.created_at as order_created_at,
                       ps.hospital_types
                from order_main o
                join institution i on i.id = o.institution_id
                left join lateral (
                    select string_agg(distinct p.hospital_type, ', ' order by p.hospital_type) as hospital_types
                    from prescription p
                    where p.order_id = o.id
                      and p.hospital_type is not null
                      and p.hospital_type <> ''
                ) ps on true
                """;
    }

    private void addOrderFilters(QueryParts query, LogisticsShipmentQuery params) {
        query.addRangeFilter("o.created_at", params.startTime(), params.endTime());
        query.addLikeFilter("i.institution_name", params.institution());
        query.addKeywordFilter(params.orderNo());
        query.addLikeFilter("o.patient_name", params.patientName());
        query.addLikeFilter("o.receiver_name", params.receiverName());
        query.addLikeFilter("o.receiver_phone", params.receiverPhone());
        query.addEqualsFilter("o.address_type", params.deliveryType());
        query.addExistsPrescriptionEquals("p.hospital_type", params.hospitalType());
    }

    private LogisticsRecords.DeliveryOrderRecord mapDeliveryOrder(ResultSet rs, int rowNum) throws SQLException {
        return new LogisticsRecords.DeliveryOrderRecord(
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("order_status"),
                rs.getString("institution_name"),
                rs.getString("patient_name"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("receiver_address"),
                rs.getString("address_type"),
                instant(rs, "delivery_time"),
                instant(rs, "order_created_at"),
                rs.getString("hospital_types")
        );
    }

    private LogisticsRecords.ShipmentRecord mapShipment(ResultSet rs, int rowNum) throws SQLException {
        return new LogisticsRecords.ShipmentRecord(
                rs.getObject("shipment_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                instant(rs, "order_created_at"),
                rs.getString("institution_name"),
                rs.getString("patient_name"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("receiver_address"),
                rs.getString("address_type"),
                instant(rs, "delivery_time"),
                rs.getString("hospital_types"),
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

    private LogisticsRecords.LogisticsInfoRecord mapLogisticsInfo(ResultSet rs, int rowNum) throws SQLException {
        return new LogisticsRecords.LogisticsInfoRecord(
                rs.getObject("trace_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("shipment_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("logistics_no"),
                rs.getString("logistics_company"),
                rs.getString("operation_info"),
                rs.getString("trace_status"),
                rs.getString("receiver_phone"),
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

    private final class QueryParts {
        private final StringBuilder sql;
        private final List<Object> args = new ArrayList<>();

        private QueryParts(String baseSql) {
            this.sql = new StringBuilder(baseSql);
        }

        private void addTextFilter(String column, String value) {
            if (StringUtils.hasText(value)) {
                append(" and " + column + " = ?");
                add(value.trim());
            }
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
            if (StringUtils.hasText(value)) {
                append(" and " + column + " ilike ?");
                add("%" + value.trim() + "%");
            }
        }

        private void addEqualsFilter(String column, String value) {
            if (StringUtils.hasText(value)) {
                append(" and " + column + " = ?");
                add(value.trim());
            }
        }

        private void addExistsPrescriptionEquals(String column, String value) {
            if (StringUtils.hasText(value)) {
                append(" and exists (select 1 from prescription p where p.order_id = o.id and " + column + " = ?)");
                add(value.trim());
            }
        }

        private void addKeywordFilter(String value) {
            if (StringUtils.hasText(value)) {
                append("""
                         and (
                            o.order_no ilike ?
                            or o.external_order_no ilike ?
                            or exists (
                                select 1 from prescription p
                                where p.order_id = o.id
                                  and (p.prescription_no ilike ? or p.external_prescription_no ilike ?)
                            )
                        )
                        """);
                String pattern = "%" + value.trim() + "%";
                add(pattern);
                add(pattern);
                add(pattern);
                add(pattern);
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
