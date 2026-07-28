package com.zhyf.report.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ReportQueryRepositoryTest {

    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final ReportQueryRepository repository = new ReportQueryRepository(jdbcTemplate);

    @Test
    void shouldConvertRangeToOffsetDateTimeWhenLoadingOverview() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.loadOverview(from, to, 14);

        Object[] args = capturedFirstCountArgs();
        assertThat(args).containsExactly(
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldBuildInstitutionPrescriptionCountQueryWithRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.loadInstitutionPrescriptionCounts(from, to);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("from prescription p")
                .contains("join institution i on i.id = p.institution_id")
                .contains("group by i.id, i.institution_code, i.institution_name");
        assertThat(argsCaptor.getValue()).containsExactly(
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldBuildDispensePerformanceQueryWithRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.loadDispensePerformance(from, to);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("from dispense_record d")
                .contains("join lateral")
                .contains("group by d.dispenser");
        assertThat(argsCaptor.getValue()).containsExactly(
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldBuildRecheckPerformanceQueryWithRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.loadRecheckPerformance(from, to);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("from workflow_task t")
                .contains("t.task_type = 'PRESCRIPTION_RECHECK'")
                .contains("group by t.assigned_to");
        assertThat(argsCaptor.getValue()).containsExactly(
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldBuildAuditPerformanceQueryWithRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.loadAuditPerformance(from, to);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("from workflow_task t")
                .contains("t.task_type = 'ORDER_REVIEW'")
                .contains("t.task_status in ('APPROVED', 'REJECTED')")
                .contains("group by t.assigned_to");
        assertThat(argsCaptor.getValue()).containsExactly(
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldBuildDecoctionPerformanceQueryWithRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.loadDecoctionPerformance(from, to);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("from decoction_device_work_record r")
                .contains("r.action_type = 'FINISH'")
                .contains("r.action_result = 'ACCEPTED'")
                .contains("left join prescription p")
                .contains("group by r.operator");
        assertThat(argsCaptor.getValue()).containsExactly(
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldBuildHerbDosageQueryWithRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.loadHerbDosage(from, to);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("from prescription_detail d")
                .contains("join prescription p on p.id = d.prescription_id")
                .contains("p.created_at")
                .contains("group by coalesce(nullif(d.platform_drug_code")
                .contains("order by total_quantity desc");
        assertThat(argsCaptor.getValue()).containsExactly(
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldBuildInstitutionHerbReconciliationQueryWithRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.loadInstitutionHerbReconciliation(from, to);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("from prescription_detail d")
                .contains("join prescription p on p.id = d.prescription_id")
                .contains("join institution i on i.id = p.institution_id")
                .contains("group by i.id")
                .contains("order by i.institution_name asc");
        assertThat(argsCaptor.getValue()).containsExactly(
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldBuildPrescriptionHerbDetailsQueryWithRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.loadPrescriptionHerbDetails(from, to);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("from prescription_detail d")
                .contains("join prescription p on p.id = d.prescription_id")
                .contains("join order_main o on o.id = p.order_id")
                .contains("join institution i on i.id = p.institution_id")
                .contains("order by p.created_at desc");
        assertThat(argsCaptor.getValue()).containsExactly(
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldBuildAuditPerformanceDetailQueryWithRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.loadAuditPerformanceDetails(from, to);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("from workflow_task t")
                .contains("t.task_type = 'ORDER_REVIEW'")
                .contains("t.task_status in ('APPROVED', 'REJECTED')")
                .contains("join order_main o on o.id = t.order_id")
                .contains("join lateral");
        assertThat(argsCaptor.getValue()).containsExactly(
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC)
        );
    }

    private Object[] capturedFirstCountArgs() {
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(anyString(), eq(Long.class), captor.capture());
        return captor.getAllValues().getFirst();
    }
}
