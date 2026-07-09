package com.zhyf.integration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
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
