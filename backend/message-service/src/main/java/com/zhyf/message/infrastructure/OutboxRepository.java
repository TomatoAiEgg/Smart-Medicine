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

    public List<OutboxEvent> fetchPublishableEvents(int limit) {
        releaseStalePublishingEvents();
        String sql = """
                select id, event_id, event_type, topic, tag, aggregate_type, aggregate_id, payload::text as payload
                from event_outbox
                where status in ('NEW', 'PUBLISH_FAILED')
                  and (next_retry_at is null or next_retry_at <= now())
                  and retry_count < max_retry_count
                order by created_at
                limit ?
                """;
        return jdbcTemplate.query(sql, this::mapEvent, limit);
    }

    public boolean markPublishing(UUID id) {
        String sql = """
                update event_outbox
                set status = 'PUBLISHING', updated_at = now()
                where id = ? and status in ('NEW', 'PUBLISH_FAILED')
                """;
        return jdbcTemplate.update(sql, id) > 0;
    }

    public void markPublished(UUID id) {
        String sql = """
                update event_outbox
                set status = 'PUBLISHED',
                    published_at = now(),
                    updated_at = now(),
                    last_error = null
                where id = ? and status = 'PUBLISHING'
                """;
        jdbcTemplate.update(sql, id);
    }

    public void markPublishFailed(UUID id, String error) {
        String sql = """
                with failed as (
                    update event_outbox
                    set status = case
                            when retry_count + 1 >= max_retry_count then 'DEAD'
                            else 'PUBLISH_FAILED'
                        end,
                        retry_count = retry_count + 1,
                        next_retry_at = case
                            when retry_count + 1 >= max_retry_count then null
                            else now() + interval '30 seconds' * least(10, retry_count + 1)
                        end,
                        last_error = ?,
                        updated_at = now()
                    where id = ? and status = 'PUBLISHING'
                    returning tenant_id, event_id, topic, tag, aggregate_id,
                              payload, last_error, retry_count, status
                )
                insert into dead_letter_record (
                    id, tenant_id, event_id, topic, tag, aggregate_id, payload_snapshot,
                    error_message, retry_count, status
                )
                select ?, tenant_id, event_id, topic, tag, aggregate_id, payload,
                       last_error, retry_count, 'OPEN'
                from failed
                where status = 'DEAD'
                  and not exists (
                    select 1
                    from dead_letter_record d
                    where d.tenant_id = failed.tenant_id
                      and d.event_id = failed.event_id
                      and d.consumer_group is null
                      and d.status = 'OPEN'
                  )
                """;
        jdbcTemplate.update(sql, truncate(error), id, UUID.randomUUID());
    }

    private void releaseStalePublishingEvents() {
        String sql = """
                update event_outbox
                set status = 'PUBLISH_FAILED',
                    next_retry_at = now(),
                    last_error = 'PUBLISHING timeout, released by scanner',
                    updated_at = now()
                where status = 'PUBLISHING'
                  and updated_at < now() - interval '5 minutes'
                """;
        jdbcTemplate.update(sql);
    }

    private OutboxEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxEvent(
                rs.getObject("id", UUID.class),
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getString("topic"),
                rs.getString("tag"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("payload")
        );
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 2000) {
            return value;
        }
        return value.substring(0, 2000);
    }
}
