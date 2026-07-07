package com.zhyf.message.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderValidationRecordRepository implements OrderValidationRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOrderValidationRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(
            UUID id,
            UUID tenantId,
            UUID orderId,
            String eventId,
            String validationStatus,
            String validationMessage,
            String rawPayload
    ) {
        String sql = """
                insert into order_validation_record (
                    id, tenant_id, order_id, event_id, validation_status, validation_message, raw_payload
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb)
                on conflict (event_id) do update
                set validation_status = excluded.validation_status,
                    validation_message = excluded.validation_message,
                    raw_payload = excluded.raw_payload
                """;
        jdbcTemplate.update(sql, id, tenantId, orderId, eventId, validationStatus, validationMessage, rawPayload);
    }
}
