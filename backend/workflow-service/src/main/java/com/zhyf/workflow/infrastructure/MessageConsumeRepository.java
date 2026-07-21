package com.zhyf.workflow.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MessageConsumeRepository {

    private static final int MAX_RETRY_COUNT = 16;

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

    public boolean markFailedRetryable(
            String consumerGroup,
            String eventId,
            String messageId,
            String topic,
            String tag,
            String aggregateId,
            String payload,
            String error
    ) {
        String sql = """
                with failed as (
                    update message_consume_log
                    set status = case
                            when retry_count + 1 >= ? then 'DEAD'
                            else 'FAILED_RETRYABLE'
                        end,
                        message_id = coalesce(?, message_id),
                        topic = coalesce(?, topic),
                        tag = coalesce(?, tag),
                        aggregate_id = coalesce(?, aggregate_id),
                        retry_count = retry_count + 1,
                        last_error = ?,
                        consume_finished_at = now(),
                        updated_at = now()
                    where consumer_group = ? and event_id = ?
                    returning event_id, topic, tag, consumer_group, aggregate_id, retry_count, last_error, status
                ),
                inserted as (
                    insert into dead_letter_record (
                        id, event_id, topic, tag, consumer_group, aggregate_id,
                        payload_snapshot, error_message, retry_count, status
                    )
                    select ?, event_id, topic, tag, consumer_group, aggregate_id,
                           coalesce(?::jsonb, '{}'::jsonb), last_error, retry_count, 'OPEN'
                    from failed
                    where status = 'DEAD'
                      and not exists (
                        select 1
                        from dead_letter_record d
                        where d.event_id = failed.event_id
                          and d.consumer_group = failed.consumer_group
                          and d.status = 'OPEN'
                      )
                    returning 1
                )
                select status from failed
                """;
        String status = jdbcTemplate.query(
                sql,
                rs -> rs.next() ? rs.getString("status") : null,
                MAX_RETRY_COUNT,
                messageId,
                topic,
                tag,
                aggregateId,
                truncate(error),
                consumerGroup,
                eventId,
                UUID.randomUUID(),
                payload
        );
        return "DEAD".equals(status);
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
