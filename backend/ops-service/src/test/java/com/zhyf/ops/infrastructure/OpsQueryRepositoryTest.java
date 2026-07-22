package com.zhyf.ops.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class OpsQueryRepositoryTest {

    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final OpsQueryRepository repository = new OpsQueryRepository(jdbcTemplate);

    @Test
    void shouldQueryOrderIdentityByOrderKeys() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.findOrderIdentity("ZHYF1", "HIS-1");

        String sql = capturedSql();
        Object[] args = capturedArgs();
        assertThat(sql).contains("from order_main");
        assertThat(sql).contains("order_no = ?");
        assertThat(sql).contains("external_order_no = ?");
        assertThat(args).containsExactly("ZHYF1", "HIS-1");
    }

    @Test
    void shouldQueryOrderMessageEvidenceByAggregateId() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.findMessageConsumeLogsByAggregateId("order-id-1", 20);

        String sql = capturedSql();
        Object[] args = capturedArgs();
        assertThat(sql).contains("from message_consume_log");
        assertThat(sql).contains("aggregate_id = ?");
        assertThat(args).containsExactly("order-id-1", 20);
    }

    @Test
    void shouldQueryRecentAccessLogsByInstitutionAndAppKey() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        UUID institutionId = UUID.randomUUID();

        repository.findRecentApiAccessLogsByInstitution(institutionId, 10);

        String sql = capturedSql();
        Object[] args = capturedArgs();
        assertThat(sql).contains("from api_access_log");
        assertThat(sql).contains("from institution_app");
        assertThat(args).containsExactly(institutionId, institutionId, 10);
    }

    @Test
    void shouldQueryIntegrationRetriesByBusinessKeys() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.findIntegrationRetriesByBusinessKeys(List.of("ZHYF1", "HIS-1"), 30);

        String sql = capturedSql();
        Object[] args = capturedArgs();
        assertThat(sql).contains("from integration_retry_task t");
        assertThat(sql).contains("where t.business_key in");
        assertThat(args).containsExactly("ZHYF1", "HIS-1", 30);
    }

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

    @Test
    void shouldQueryIntegrationRetryIssuesWithDefaultFailedStatuses() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.findIntegrationRetryIssues(null, "ADDRESS_PUSH", "ZHYF1", "HOSP-E2E", 50);

        String sql = capturedSql();
        Object[] args = capturedArgs();
        assertThat(sql).contains("from integration_retry_task t");
        assertThat(sql).contains("join integration_message m on m.id = t.message_id");
        assertThat(sql).contains("t.task_status in ('FAILED', 'DEAD')");
        assertThat(sql).contains("t.task_type = ?");
        assertThat(sql).contains("t.business_key = ?");
        assertThat(sql).contains("m.source_system = ?");
        assertThat(args).containsExactly("ADDRESS_PUSH", "ZHYF1", "HOSP-E2E", 50);
    }

    @Test
    void shouldLoadHealthOverviewFromCoreTables() {
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class)))
                .thenReturn(1L);

        var overview = repository.loadHealthOverview(24);

        assertThat(overview.pendingOutbox()).isEqualTo(1);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce())
                .queryForObject(sqlCaptor.capture(), org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class));
        assertThat(sqlCaptor.getAllValues()).anyMatch(sql -> sql.contains("from event_outbox") && sql.contains("status = ?"));
        assertThat(sqlCaptor.getAllValues()).anyMatch(sql -> sql.contains("from message_consume_log"));
        assertThat(sqlCaptor.getAllValues()).anyMatch(sql -> sql.contains("from callback_record"));
        assertThat(sqlCaptor.getAllValues()).anyMatch(sql -> sql.contains("from integration_retry_task"));
    }

    @Test
    void shouldQueryOpenDeadLettersByDefault() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.findDeadLetters(null, "zhyf-order-event", "event-1", 50);

        String sql = capturedSql();
        Object[] args = capturedArgs();
        assertThat(sql).contains("from dead_letter_record");
        assertThat(sql).contains("status = ?");
        assertThat(sql).contains("topic = ?");
        assertThat(sql).contains("event_id = ?");
        assertThat(args).containsExactly("OPEN", "zhyf-order-event", "event-1", 50);
    }

    @Test
    void shouldResetOutboxWhenReplayDeadLetter() {
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class)))
                .thenReturn(1L);

        int resetCount = repository.resetDeadLetterForReplay(id);

        assertThat(resetCount).isEqualTo(1);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(Long.class),
                any(Object[].class)
        );
        assertThat(sqlCaptor.getValue()).contains("update event_outbox");
        assertThat(sqlCaptor.getValue()).contains("update message_consume_log");
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
