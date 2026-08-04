package com.zhyf.callback.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CallbackRepositoryTest {

    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final CallbackRepository repository = new CallbackRepository(jdbcTemplate);

    @Test
    void shouldConvertDueQueryTimeToOffsetDateTime() {
        Instant now = Instant.parse("2026-07-08T00:00:00Z");
        when(jdbcTemplate.query(anyString(), anyRowMapper(), any(Object[].class))).thenReturn(java.util.List.of());

        repository.claimDueRecords("worker:claim", now, now.plusSeconds(120), 10);

        Object[] args = capturedQueryArgs();
        assertThat(args[0]).isEqualTo(OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        assertThat(args[1]).isEqualTo(OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        assertThat(args[3]).isEqualTo("worker:claim");
        assertThat(args[5]).isEqualTo(OffsetDateTime.ofInstant(now.plusSeconds(120), ZoneOffset.UTC));
    }

    @Test
    void shouldConvertNextRetryAtToOffsetDateTimeWhenMarkingFailed() {
        Instant nextRetryAt = Instant.parse("2026-07-08T00:05:00Z");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.markFailed(UUID.randomUUID(), "timeout", nextRetryAt);

        Object[] args = capturedUpdateArgs();
        assertThat(args[2]).isEqualTo(OffsetDateTime.ofInstant(nextRetryAt, ZoneOffset.UTC));
    }

    @Test
    void shouldConvertNextRetryAtToOffsetDateTimeWhenReplaying() {
        Instant nextRetryAt = Instant.parse("2026-07-08T00:06:00Z");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.replay(UUID.randomUUID(), nextRetryAt);

        Object[] args = capturedUpdateArgs();
        assertThat(args[0]).isEqualTo(OffsetDateTime.ofInstant(nextRetryAt, ZoneOffset.UTC));
    }

    private Object[] capturedUpdateArgs() {
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        return captor.getValue();
    }

    private Object[] capturedQueryArgs() {
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), anyRowMapper(), captor.capture());
        return captor.getValue();
    }

    private static <T> RowMapper<T> anyRowMapper() {
        return any();
    }
}
