package com.zhyf.ops.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class OpsQueryRepositoryTest {

    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final OpsQueryRepository repository = new OpsQueryRepository(jdbcTemplate);

    @Test
    void shouldJoinCallbackBusinessIdByShipmentIdOrLogisticsNo() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.findLogisticsCallbackIssues(null, null, "E2E-LC-1", null, 50);

        String sql = capturedSql();
        Object[] args = capturedArgs();
        assertThat(sql).contains("c.business_id = s.id::text");
        assertThat(sql).contains("c.business_id like s.id::text || ':%'");
        assertThat(sql).contains("c.business_id = ? or s.logistics_no = ?");
        assertThat(args).containsExactly("E2E-LC-1", "E2E-LC-1", 50);
    }

    private String capturedSql() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(captor.capture(), any(RowMapper.class), any(Object[].class));
        return captor.getValue();
    }

    private Object[] capturedArgs() {
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), captor.capture());
        return captor.getValue();
    }
}
