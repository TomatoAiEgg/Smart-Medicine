package com.zhyf.decoction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.decoction.domain.DecoctionTaskSnapshot;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class DecoctionTaskRepositoryTest {

    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final DecoctionTaskRepository repository = new DecoctionTaskRepository(jdbcTemplate);

    @Test
    void shouldConvertTaskEventTimeToOffsetDateTime() {
        Instant eventTime = Instant.parse("2026-07-09T05:30:00Z");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.createTaskEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "WATER_FINISHED",
                "OP-1",
                "legacy-e2e",
                "{}",
                eventTime
        );

        Object[] args = capturedUpdateArgs();
        assertThat(args[8]).isEqualTo(OffsetDateTime.ofInstant(eventTime, ZoneOffset.UTC));
    }

    @Test
    void shouldConvertDeviceWorkActionTimeToOffsetDateTime() {
        Instant actionTime = Instant.parse("2026-07-09T05:31:00Z");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.createDeviceWorkRecord(
                UUID.randomUUID(),
                task(),
                "BIND",
                "SUCCESS",
                null,
                "BOUND",
                "OP-2",
                "legacy-e2e",
                "operator",
                "{}",
                actionTime
        );

        Object[] args = capturedUpdateArgs();
        assertThat(args[16]).isEqualTo(OffsetDateTime.ofInstant(actionTime, ZoneOffset.UTC));
    }

    private Object[] capturedUpdateArgs() {
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        return captor.getValue();
    }

    private DecoctionTaskSnapshot task() {
        return new DecoctionTaskSnapshot(
                UUID.randomUUID(),
                "DCT-TEST",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ZHYF1",
                "RX1",
                "DECOCT-001",
                "PAIL-1",
                "BOUND",
                "operator",
                null,
                null,
                Instant.parse("2026-07-09T05:00:00Z"),
                Instant.parse("2026-07-09T05:00:00Z")
        );
    }
}
