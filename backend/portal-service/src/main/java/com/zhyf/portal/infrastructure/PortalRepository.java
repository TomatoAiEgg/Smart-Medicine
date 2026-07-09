package com.zhyf.portal.infrastructure;

import com.zhyf.portal.application.PortalCommands;
import com.zhyf.portal.application.PortalRecords;
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
import org.springframework.util.StringUtils;

@Repository
public class PortalRepository {

    private final JdbcTemplate jdbcTemplate;

    public PortalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PortalRecords.PortalOrderRecord> findOrderForPortal(
            String orderNo,
            String externalOrderNo,
            String phone
    ) {
        QueryParts query = new QueryParts("""
                select o.tenant_id, o.id as order_id, i.institution_name, o.order_no, o.external_order_no,
                       o.status as order_status, o.patient_name, o.patient_phone,
                       o.receiver_name, o.receiver_phone,
                       concat_ws('', o.receiver_province, o.receiver_city, o.receiver_zone, o.receiver_address)
                           as receiver_address,
                       o.created_at
                from order_main o
                join institution i on i.id = o.institution_id
                where 1 = 1
                """);
        if (StringUtils.hasText(orderNo)) {
            query.append(" and o.order_no = ?");
            query.add(orderNo);
        }
        if (StringUtils.hasText(externalOrderNo)) {
            query.append(" and o.external_order_no = ?");
            query.add(externalOrderNo);
        }
        query.append(" and (o.patient_phone = ? or o.receiver_phone = ?)");
        query.add(phone);
        query.add(phone);
        query.append(" order by o.created_at desc limit 1");

        return jdbcTemplate.query(query.sql(), (rs, rowNum) -> mapOrder(rs), query.args()).stream().findFirst();
    }

    public PortalRecords.AddressSupplementRecord createAddressSupplement(
            UUID supplementId,
            PortalRecords.PortalOrderRecord order,
            PortalCommands.AddressSupplementCommand command,
            String rawPayload
    ) {
        Instant createdAt = Instant.now();
        String sql = """
                insert into portal_address_supplement (
                    id, tenant_id, order_id, order_no, supplement_status,
                    receiver_name, receiver_phone, receiver_province, receiver_city, receiver_zone, receiver_address,
                    requester_name, requester_phone, remark, raw_payload, created_at, updated_at
                ) values (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                supplementId,
                order.tenantId(),
                order.orderId(),
                order.orderNo(),
                command.receiverName(),
                command.receiverPhone(),
                command.receiverProvince(),
                command.receiverCity(),
                command.receiverZone(),
                command.receiverAddress(),
                command.requesterName(),
                command.requesterPhone(),
                command.remark(),
                rawPayload == null ? "{}" : rawPayload,
                offsetDateTime(createdAt),
                offsetDateTime(createdAt)
        );
        return new PortalRecords.AddressSupplementRecord(
                supplementId,
                order.tenantId(),
                order.orderId(),
                order.orderNo(),
                "PENDING",
                command.receiverName(),
                command.receiverPhone(),
                command.receiverProvince(),
                command.receiverCity(),
                command.receiverZone(),
                command.receiverAddress(),
                command.requesterName(),
                command.requesterPhone(),
                command.remark(),
                createdAt
        );
    }

    private PortalRecords.PortalOrderRecord mapOrder(ResultSet rs) throws SQLException {
        UUID orderId = rs.getObject("order_id", UUID.class);
        return new PortalRecords.PortalOrderRecord(
                rs.getObject("tenant_id", UUID.class),
                orderId,
                rs.getString("institution_name"),
                rs.getString("order_no"),
                rs.getString("external_order_no"),
                rs.getString("order_status"),
                rs.getString("patient_name"),
                rs.getString("patient_phone"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("receiver_address"),
                instant(rs, "created_at"),
                findPrescriptions(orderId),
                findShipment(orderId).orElse(null)
        );
    }

    private List<PortalRecords.PrescriptionRecord> findPrescriptions(UUID orderId) {
        String sql = """
                select prescription_no, status as prescription_status, prescription_type, doctor_name, diagnosis
                from prescription
                where order_id = ?
                order by created_at asc
                """;
        return jdbcTemplate.query(sql, this::mapPrescription, orderId);
    }

    private Optional<PortalRecords.ShipmentRecord> findShipment(UUID orderId) {
        String sql = """
                select s.logistics_no, s.logistics_company, s.logistics_status,
                       (select max(st.trace_time) from shipment_trace st where st.shipment_id = s.id) as latest_trace_time
                from shipment s
                where s.order_id = ?
                order by s.created_at desc
                limit 1
                """;
        return jdbcTemplate.query(sql, this::mapShipment, orderId).stream().findFirst();
    }

    private PortalRecords.PrescriptionRecord mapPrescription(ResultSet rs, int rowNum) throws SQLException {
        return new PortalRecords.PrescriptionRecord(
                rs.getString("prescription_no"),
                rs.getString("prescription_status"),
                rs.getString("prescription_type"),
                rs.getString("doctor_name"),
                rs.getString("diagnosis")
        );
    }

    private PortalRecords.ShipmentRecord mapShipment(ResultSet rs, int rowNum) throws SQLException {
        return new PortalRecords.ShipmentRecord(
                rs.getString("logistics_no"),
                rs.getString("logistics_company"),
                rs.getString("logistics_status"),
                instant(rs, "latest_trace_time")
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
        private final java.util.List<Object> args = new java.util.ArrayList<>();

        private QueryParts(String baseSql) {
            this.sql = new StringBuilder(baseSql);
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
