package com.zhyf.report.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.report.infrastructure.ReportQueryRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReportQueryService {

    private static final int DEFAULT_TREND_DAYS = 14;
    private static final int MAX_TREND_DAYS = 60;

    private final ReportQueryRepository repository;

    public ReportQueryService(ReportQueryRepository repository) {
        this.repository = repository;
    }

    public ReportRecords.ReportOverview overview(Instant from, Instant to, int trendDays) {
        validateTimeRange(from, to);
        return repository.loadOverview(from, to, normalizeTrendDays(trendDays));
    }

    public String exportOverviewCsv(Instant from, Instant to, int trendDays) {
        ReportRecords.ReportOverview overview = overview(from, to, trendDays);
        StringBuilder csv = new StringBuilder("section,item,count\n");
        append(csv, "summary", "totalOrders", overview.totalOrders());
        append(csv, "summary", "totalPrescriptions", overview.totalPrescriptions());
        append(csv, "summary", "totalShipments", overview.totalShipments());
        append(csv, "summary", "totalCallbacks", overview.totalCallbacks());
        append(csv, "summary", "pendingAddressSupplements", overview.pendingAddressSupplements());
        overview.orderStatusCounts().forEach(item -> append(csv, "orderStatus", item.status(), item.count()));
        overview.callbackStatusCounts().forEach(item -> append(csv, "callbackStatus", item.status(), item.count()));
        overview.dailyOrderCounts().forEach(item -> append(csv, "dailyOrder", item.day().toString(), item.count()));
        return csv.toString();
    }

    public List<ReportRecords.InstitutionPrescriptionCount> institutionPrescriptionCounts(Instant from, Instant to) {
        validateTimeRange(from, to);
        return repository.loadInstitutionPrescriptionCounts(from, to);
    }

    public String exportInstitutionPrescriptionCountsCsv(Instant from, Instant to) {
        List<ReportRecords.InstitutionPrescriptionCount> rows = institutionPrescriptionCounts(from, to);
        StringBuilder csv = new StringBuilder("institutionCode,institutionName,orderCount,prescriptionCount,doseCount,totalAmount\n");
        rows.forEach(row -> csv.append(escape(row.institutionCode()))
                .append(',')
                .append(escape(row.institutionName()))
                .append(',')
                .append(row.orderCount())
                .append(',')
                .append(row.prescriptionCount())
                .append(',')
                .append(row.doseCount())
                .append(',')
                .append(row.totalAmount())
                .append('\n'));
        return csv.toString();
    }

    public List<ReportRecords.DispensePerformance> dispensePerformance(Instant from, Instant to) {
        validateTimeRange(from, to);
        return repository.loadDispensePerformance(from, to);
    }

    public String exportDispensePerformanceCsv(Instant from, Instant to) {
        List<ReportRecords.DispensePerformance> rows = dispensePerformance(from, to);
        StringBuilder csv = new StringBuilder("dispenser,dispenseCount,orderCount,prescriptionCount,doseCount,firstDispensedAt,lastDispensedAt\n");
        rows.forEach(row -> csv.append(escape(row.dispenser()))
                .append(',')
                .append(row.dispenseCount())
                .append(',')
                .append(row.orderCount())
                .append(',')
                .append(row.prescriptionCount())
                .append(',')
                .append(row.doseCount())
                .append(',')
                .append(row.firstDispensedAt() == null ? "" : row.firstDispensedAt())
                .append(',')
                .append(row.lastDispensedAt() == null ? "" : row.lastDispensedAt())
                .append('\n'));
        return csv.toString();
    }

    public List<ReportRecords.RecheckPerformance> recheckPerformance(Instant from, Instant to) {
        validateTimeRange(from, to);
        return repository.loadRecheckPerformance(from, to);
    }

    public String exportRecheckPerformanceCsv(Instant from, Instant to) {
        List<ReportRecords.RecheckPerformance> rows = recheckPerformance(from, to);
        StringBuilder csv = new StringBuilder("rechecker,recheckCount,orderCount,prescriptionCount,doseCount,firstRecheckedAt,lastRecheckedAt\n");
        rows.forEach(row -> csv.append(escape(row.rechecker()))
                .append(',')
                .append(row.recheckCount())
                .append(',')
                .append(row.orderCount())
                .append(',')
                .append(row.prescriptionCount())
                .append(',')
                .append(row.doseCount())
                .append(',')
                .append(row.firstRecheckedAt() == null ? "" : row.firstRecheckedAt())
                .append(',')
                .append(row.lastRecheckedAt() == null ? "" : row.lastRecheckedAt())
                .append('\n'));
        return csv.toString();
    }

    public List<ReportRecords.AuditPerformance> auditPerformance(Instant from, Instant to) {
        validateTimeRange(from, to);
        return repository.loadAuditPerformance(from, to);
    }

    public String exportAuditPerformanceCsv(Instant from, Instant to) {
        List<ReportRecords.AuditPerformance> rows = auditPerformance(from, to);
        StringBuilder csv = new StringBuilder("auditor,auditCount,approvedCount,rejectedCount,orderCount,prescriptionCount,doseCount,firstAuditedAt,lastAuditedAt\n");
        rows.forEach(row -> csv.append(escape(row.auditor()))
                .append(',')
                .append(row.auditCount())
                .append(',')
                .append(row.approvedCount())
                .append(',')
                .append(row.rejectedCount())
                .append(',')
                .append(row.orderCount())
                .append(',')
                .append(row.prescriptionCount())
                .append(',')
                .append(row.doseCount())
                .append(',')
                .append(row.firstAuditedAt() == null ? "" : row.firstAuditedAt())
                .append(',')
                .append(row.lastAuditedAt() == null ? "" : row.lastAuditedAt())
                .append('\n'));
        return csv.toString();
    }

    private void append(StringBuilder csv, String section, String item, long count) {
        csv.append(escape(section))
                .append(',')
                .append(escape(item))
                .append(',')
                .append(count)
                .append('\n');
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private int normalizeTrendDays(int trendDays) {
        if (trendDays <= 0) {
            return DEFAULT_TREND_DAYS;
        }
        return Math.min(trendDays, MAX_TREND_DAYS);
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BusinessException("REPORT_INVALID_TIME_RANGE", "From time must be before to time");
        }
    }
}
