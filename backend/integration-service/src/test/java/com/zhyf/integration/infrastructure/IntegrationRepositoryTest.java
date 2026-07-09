package com.zhyf.integration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class IntegrationRepositoryTest {

    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final IntegrationRepository repository = new IntegrationRepository(jdbcTemplate);

    @Test
    void shouldPersistIntegrationMessageWithOffsetDateTime() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.createMessage(
                UUID.randomUUID(),
                "COMMUNITY_HOSPITAL",
                "CH-001",
                "MSG-001",
                "ORDER_CREATED",
                "CH-ORDER-1",
                "{}",
                "{}"
        );

        Object[] args = capturedUpdateArgs();
        assertThat(args[10]).isInstanceOf(OffsetDateTime.class);
        assertThat(args[11]).isInstanceOf(OffsetDateTime.class);
    }

    @Test
    void shouldFilterHospitalQueryByPrescriptionNoAndPhone() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.findHospitalOrderByPrescription("RX1", "13800000000");

        String sql = capturedQuerySql();
        Object[] args = capturedQueryArgs();
        assertThat(sql).contains("p.prescription_no = ?");
        assertThat(sql).contains("o.patient_phone = ? or o.receiver_phone = ?");
        assertThat(args).containsExactly("RX1", "13800000000", "13800000000");
    }

    @Test
    void shouldConvertRetryTaskTimesToOffsetDateTime() {
        Instant nextRetryAt = Instant.parse("2026-07-09T08:05:00Z");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.createRetryTask(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ADDRESS_PUSH",
                "HOSP-001",
                "ZHYF1",
                "http://127.0.0.1:19999/address",
                "{}",
                nextRetryAt
        );

        Object[] args = capturedUpdateArgs();
        assertThat(args[10]).isEqualTo(OffsetDateTime.ofInstant(nextRetryAt, ZoneOffset.UTC));
        assertThat(args[11]).isInstanceOf(OffsetDateTime.class);
        assertThat(args[12]).isInstanceOf(OffsetDateTime.class);
    }

    @Test
    void shouldConvertDueRetryTaskQueryTimeToOffsetDateTime() {
        Instant now = Instant.parse("2026-07-09T08:00:00Z");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.findDueRetryTasks(now, 10);

        Object[] args = capturedQueryArgs();
        assertThat(args[0]).isEqualTo(OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    @Test
    void shouldConvertRetryTaskFailureNextRetryTimeToOffsetDateTime() {
        Instant nextRetryAt = Instant.parse("2026-07-09T08:10:00Z");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.markRetryTaskFailed(UUID.randomUUID(), "timeout", nextRetryAt);

        Object[] args = capturedUpdateArgs();
        assertThat(args[1]).isEqualTo(OffsetDateTime.ofInstant(nextRetryAt, ZoneOffset.UTC));
    }

    private Object[] capturedUpdateArgs() {
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        return captor.getValue();
    }

    private String capturedQuerySql() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(captor.capture(), any(RowMapper.class), any(Object[].class));
        return captor.getValue();
    }

    private Object[] capturedQueryArgs() {
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), captor.capture());
        return captor.getValue();
    }
}
