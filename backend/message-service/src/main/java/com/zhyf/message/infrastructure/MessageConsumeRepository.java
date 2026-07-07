package com.zhyf.message.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MessageConsumeRepository {

    private final JdbcTemplate jdbcTemplate;

    public MessageConsumeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryBegin(String consumerGroup, String eventId, String messageId) {
        String sql = """
                insert into message_consume_log (
                    id, consumer_group, message_id, event_id, status
                ) values (?, ?, ?, ?, 'RECEIVED')
                on conflict (consumer_group, event_id) do nothing
                """;
        return jdbcTemplate.update(sql, UUID.randomUUID(), consumerGroup, messageId, eventId) > 0;
    }

    public Optional<String> findStatus(String consumerGroup, String eventId) {
        String sql = """
                select status
                from message_consume_log
                where consumer_group = ? and event_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapStatus, consumerGroup, eventId).stream().findFirst();
    }

    public int markProcessing(String consumerGroup, String eventId, String messageId) {
        String sql = """
                update message_consume_log
                set status = 'PROCESSING',
                    message_id = coalesce(?, message_id)
                where consumer_group = ? and event_id = ? and status in ('FAILED', 'RECEIVED')
                """;
        return jdbcTemplate.update(sql, messageId, consumerGroup, eventId);
    }

    public int markSuccess(String consumerGroup, String eventId, String messageId) {
        String sql = """
                update message_consume_log
                set status = 'SUCCESS',
                    message_id = coalesce(?, message_id)
                where consumer_group = ? and event_id = ?
                """;
        return jdbcTemplate.update(sql, messageId, consumerGroup, eventId);
    }

    public int markFailed(String consumerGroup, String eventId, String messageId) {
        String sql = """
                update message_consume_log
                set status = 'FAILED',
                    message_id = coalesce(?, message_id)
                where consumer_group = ? and event_id = ?
                """;
        return jdbcTemplate.update(sql, messageId, consumerGroup, eventId);
    }

    private String mapStatus(ResultSet rs, int rowNum) throws SQLException {
        return rs.getString("status");
    }
}
