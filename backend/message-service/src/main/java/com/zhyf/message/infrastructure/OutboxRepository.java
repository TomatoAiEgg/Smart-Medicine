package com.zhyf.message.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OutboxEvent> fetchNewEvents(int limit) {
        String sql = """
                select id, event_id, event_type, aggregate_type, aggregate_id, payload::text as payload
                from event_outbox
                where status = 'NEW'
                order by created_at
                limit ?
                """;
        return jdbcTemplate.query(sql, this::mapEvent, limit);
    }

    public void markSent(UUID id) {
        String sql = """
                update event_outbox
                set status = 'SENT', published_at = now()
                where id = ? and status = 'NEW'
                """;
        jdbcTemplate.update(sql, id);
    }

    public void markFailed(UUID id) {
        String sql = """
                update event_outbox
                set status = 'FAILED', retry_count = retry_count + 1, next_retry_at = now() + interval '30 seconds'
                where id = ?
                """;
        jdbcTemplate.update(sql, id);
    }

    private OutboxEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxEvent(
                rs.getObject("id", UUID.class),
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("payload")
        );
    }
}
