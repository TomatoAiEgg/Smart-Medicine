package com.zhyf.workflow.infrastructure;

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

    public boolean tryBegin(
            String consumerGroup,
            String eventId,
            String messageId,
            String topic,
            String tag,
            String aggregateId
    ) {
        String sql = """
                insert into message_consume_log (
                    id, consumer_group, message_id, event_id, topic, tag, aggregate_id,
                    status, consume_started_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'PROCESSING', now(), now())
                on conflict (consumer_group, event_id) do nothing
                """;
        return jdbcTemplate.update(
                sql,
                UUID.randomUUID(),
                consumerGroup,
                messageId,
                eventId,
                topic,
                tag,
                aggregateId
        ) > 0;
    }

    public Optional<String> findStatus(String consumerGroup, String eventId) {
        String sql = """
                select status
                from message_consume_log
                where consumer_group = ? and event_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapStatus, consumerGroup, eventId).stream().findFirst();
    }

    public int markProcessing(
            String consumerGroup,
            String eventId,
            String messageId,
            String topic,
            String tag,
            String aggregateId
    ) {
        String sql = """
                update message_consume_log
                set status = 'PROCESSING',
                    message_id = coalesce(?, message_id),
                    topic = coalesce(?, topic),
                    tag = coalesce(?, tag),
                    aggregate_id = coalesce(?, aggregate_id),
                    consume_started_at = now(),
                    updated_at = now()
                where consumer_group = ? and event_id = ?
                  and (
                    status in ('RECEIVED', 'FAILED', 'FAILED_RETRYABLE')
                    or (status = 'PROCESSING' and updated_at < now() - interval '5 minutes')
                  )
                """;
        return jdbcTemplate.update(sql, messageId, topic, tag, aggregateId, consumerGroup, eventId);
    }

    public int markSuccess(String consumerGroup, String eventId, String messageId) {
        String sql = """
                update message_consume_log
                set status = 'SUCCESS',
                    message_id = coalesce(?, message_id),
                    consume_finished_at = now(),
                    last_error = null,
                    updated_at = now()
                where consumer_group = ? and event_id = ?
                """;
        return jdbcTemplate.update(sql, messageId, consumerGroup, eventId);
    }

    public int markFailedRetryable(String consumerGroup, String eventId, String messageId, String error) {
        String sql = """
                update message_consume_log
                set status = 'FAILED_RETRYABLE',
                    message_id = coalesce(?, message_id),
                    retry_count = retry_count + 1,
                    last_error = ?,
                    consume_finished_at = now(),
                    updated_at = now()
                where consumer_group = ? and event_id = ?
                """;
        return jdbcTemplate.update(sql, messageId, truncate(error), consumerGroup, eventId);
    }

    private String mapStatus(ResultSet rs, int rowNum) throws SQLException {
        return rs.getString("status");
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 2000) {
            return value;
        }
        return value.substring(0, 2000);
    }
}
