package com.zhyf.report.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.report.infrastructure.ReportQueryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ReportQueryServiceTest {

    private final ReportQueryRepository repository = Mockito.mock(ReportQueryRepository.class);
    private final ReportQueryService service = new ReportQueryService(repository);

    @Test
    void shouldUseDefaultTrendDaysWhenTrendDaysIsInvalid() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(repository.loadOverview(from, to, 14)).thenReturn(emptyOverview(from, to, 14));

        service.overview(from, to, 0);

        verify(repository).loadOverview(from, to, 14);
    }

    @Test
    void shouldCapTrendDaysAtMaxLimit() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(repository.loadOverview(from, to, 60)).thenReturn(emptyOverview(from, to, 60));

        service.overview(from, to, 100);

        verify(repository).loadOverview(from, to, 60);
    }

    @Test
    void shouldRejectInvalidTimeRange() {
        Instant from = Instant.parse("2026-07-10T00:00:00Z");
        Instant to = Instant.parse("2026-07-01T00:00:00Z");

        assertThatThrownBy(() -> service.overview(from, to, 14))
                .isInstanceOf(BusinessException.class)
                .hasMessage("From time must be before to time");
    }

    @Test
    void shouldExportOverviewAsCsv() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(repository.loadOverview(from, to, 14)).thenReturn(new ReportRecords.ReportOverview(
                from,
                to,
                14,
                2,
                3,
                1,
                4,
                1,
                List.of(new ReportRecords.StatusCount("RECHECKED", 2)),
                List.of(new ReportRecords.StatusCount("FAILED", 1)),
                List.of(new ReportRecords.DailyOrderCount(java.time.LocalDate.parse("2026-07-01"), 2))
        ));

        String csv = service.exportOverviewCsv(from, to, 14);

        assertThat(csv).startsWith("section,item,count");
        assertThat(csv).contains("summary,totalOrders,2");
        assertThat(csv).contains("orderStatus,RECHECKED,2");
        assertThat(csv).contains("callbackStatus,FAILED,1");
        assertThat(csv).contains("dailyOrder,2026-07-01,2");
    }

    @Test
    void shouldExportInstitutionPrescriptionCountsAsCsv() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(repository.loadInstitutionPrescriptionCounts(from, to)).thenReturn(List.of(
                new ReportRecords.InstitutionPrescriptionCount("inst-1", "H001", "测试医院", 2, 3, 18, new BigDecimal("126.50"))
        ));

        String csv = service.exportInstitutionPrescriptionCountsCsv(from, to);

        verify(repository).loadInstitutionPrescriptionCounts(from, to);
        assertThat(csv).startsWith("institutionCode,institutionName,orderCount,prescriptionCount,doseCount,totalAmount");
        assertThat(csv).contains("H001,测试医院,2,3,18,126.50");
    }

    @Test
    void shouldExportDispensePerformanceAsCsv() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        when(repository.loadDispensePerformance(from, to)).thenReturn(List.of(
                new ReportRecords.DispensePerformance(
                        "D001",
                        4,
                        3,
                        5,
                        24,
                        Instant.parse("2026-07-02T01:00:00Z"),
                        Instant.parse("2026-07-09T02:00:00Z")
                )
        ));

        String csv = service.exportDispensePerformanceCsv(from, to);

        verify(repository).loadDispensePerformance(from, to);
        assertThat(csv).startsWith("dispenser,dispenseCount,orderCount,prescriptionCount,doseCount,firstDispensedAt,lastDispensedAt");
        assertThat(csv).contains("D001,4,3,5,24,2026-07-02T01:00:00Z,2026-07-09T02:00:00Z");
    }

    private ReportRecords.ReportOverview emptyOverview(Instant from, Instant to, int trendDays) {
        return new ReportRecords.ReportOverview(
                from,
                to,
                trendDays,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
