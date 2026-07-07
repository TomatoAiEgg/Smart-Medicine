package com.zhyf.workflow.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderValidationRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderValidationRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<OrderValidationRecord> findLatestByOrderId(UUID orderId) {
        String sql = """
                select tenant_id, order_id, event_id, validation_status, validation_message, raw_payload::text as raw_payload
                from order_validation_record
                where order_id = ?
                order by created_at desc
                limit 1
                """;
        return jdbcTemplate.query(sql, this::mapRecord, orderId).stream().findFirst();
    }

    private OrderValidationRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OrderValidationRecord(
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("event_id"),
                rs.getString("validation_status"),
                rs.getString("validation_message"),
                rs.getString("raw_payload")
        );
    }

    public record OrderValidationRecord(
            UUID tenantId,
            UUID orderId,
            String eventId,
            String validationStatus,
            String validationMessage,
            String rawPayload
    ) {
    }
}
