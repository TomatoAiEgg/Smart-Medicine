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

    public List<ReportRecords.DecoctionPerformance> decoctionPerformance(Instant from, Instant to) {
        validateTimeRange(from, to);
        return repository.loadDecoctionPerformance(from, to);
    }

    public String exportDecoctionPerformanceCsv(Instant from, Instant to) {
        List<ReportRecords.DecoctionPerformance> rows = decoctionPerformance(from, to);
        StringBuilder csv = new StringBuilder("operator,decoctionCount,orderCount,prescriptionCount,doseCount,deviceCount,firstFinishedAt,lastFinishedAt\n");
        rows.forEach(row -> csv.append(escape(row.operator()))
                .append(',')
                .append(row.decoctionCount())
                .append(',')
                .append(row.orderCount())
                .append(',')
                .append(row.prescriptionCount())
                .append(',')
                .append(row.doseCount())
                .append(',')
                .append(row.deviceCount())
                .append(',')
                .append(row.firstFinishedAt() == null ? "" : row.firstFinishedAt())
                .append(',')
                .append(row.lastFinishedAt() == null ? "" : row.lastFinishedAt())
                .append('\n'));
        return csv.toString();
    }

    public List<ReportRecords.HerbDosage> herbDosage(Instant from, Instant to) {
        validateTimeRange(from, to);
        return repository.loadHerbDosage(from, to);
    }

    public String exportHerbDosageCsv(Instant from, Instant to) {
        List<ReportRecords.HerbDosage> rows = herbDosage(from, to);
        StringBuilder csv = new StringBuilder("herbCode,herbName,drugSpecs,drugOrigin,unit,detailCount,prescriptionCount,orderCount,totalQuantity,totalAmount,settlementAmount\n");
        rows.forEach(row -> csv.append(escape(row.herbCode()))
                .append(',')
                .append(escape(row.herbName()))
                .append(',')
                .append(escape(row.drugSpecs()))
                .append(',')
                .append(escape(row.drugOrigin()))
                .append(',')
                .append(escape(row.unit()))
                .append(',')
                .append(row.detailCount())
                .append(',')
                .append(row.prescriptionCount())
                .append(',')
                .append(row.orderCount())
                .append(',')
                .append(row.totalQuantity())
                .append(',')
                .append(row.totalAmount())
                .append(',')
                .append(row.settlementAmount())
                .append('\n'));
        return csv.toString();
    }

    public List<ReportRecords.InstitutionHerbReconciliation> institutionHerbReconciliation(Instant from, Instant to) {
        validateTimeRange(from, to);
        return repository.loadInstitutionHerbReconciliation(from, to);
    }

    public String exportInstitutionHerbReconciliationCsv(Instant from, Instant to) {
        List<ReportRecords.InstitutionHerbReconciliation> rows = institutionHerbReconciliation(from, to);
        StringBuilder csv = new StringBuilder("institutionCode,institutionName,herbCode,herbName,drugSpecs,drugOrigin,unit,detailCount,prescriptionCount,orderCount,totalQuantity,totalAmount,settlementAmount\n");
        rows.forEach(row -> csv.append(escape(row.institutionCode()))
                .append(',')
                .append(escape(row.institutionName()))
                .append(',')
                .append(escape(row.herbCode()))
                .append(',')
                .append(escape(row.herbName()))
                .append(',')
                .append(escape(row.drugSpecs()))
                .append(',')
                .append(escape(row.drugOrigin()))
                .append(',')
                .append(escape(row.unit()))
                .append(',')
                .append(row.detailCount())
                .append(',')
                .append(row.prescriptionCount())
                .append(',')
                .append(row.orderCount())
                .append(',')
                .append(row.totalQuantity())
                .append(',')
                .append(row.totalAmount())
                .append(',')
                .append(row.settlementAmount())
                .append('\n'));
        return csv.toString();
    }

    public List<ReportRecords.PrescriptionHerbDetail> prescriptionHerbDetails(Instant from, Instant to) {
        validateTimeRange(from, to);
        return repository.loadPrescriptionHerbDetails(from, to);
    }

    public String exportPrescriptionHerbDetailsCsv(Instant from, Instant to) {
        List<ReportRecords.PrescriptionHerbDetail> rows = prescriptionHerbDetails(from, to);
        StringBuilder csv = new StringBuilder("institutionCode,institutionName,orderNo,externalOrderNo,prescriptionNo,externalPrescriptionNo,herbCode,herbName,drugSpecs,drugOrigin,dose,unit,specialUsage,quantity,unitPrice,totalPrice,settlementUnitPrice,settlementTotalPrice,batchNo,remark,prescriptionCreatedAt\n");
        rows.forEach(row -> csv.append(escape(row.institutionCode()))
                .append(',')
                .append(escape(row.institutionName()))
                .append(',')
                .append(escape(row.orderNo()))
                .append(',')
                .append(escape(row.externalOrderNo()))
                .append(',')
                .append(escape(row.prescriptionNo()))
                .append(',')
                .append(escape(row.externalPrescriptionNo()))
                .append(',')
                .append(escape(row.herbCode()))
                .append(',')
                .append(escape(row.herbName()))
                .append(',')
                .append(escape(row.drugSpecs()))
                .append(',')
                .append(escape(row.drugOrigin()))
                .append(',')
                .append(escape(row.dose()))
                .append(',')
                .append(escape(row.unit()))
                .append(',')
                .append(escape(row.specialUsage()))
                .append(',')
                .append(row.quantity() == null ? "" : row.quantity())
                .append(',')
                .append(row.unitPrice() == null ? "" : row.unitPrice())
                .append(',')
                .append(row.totalPrice() == null ? "" : row.totalPrice())
                .append(',')
                .append(row.settlementUnitPrice() == null ? "" : row.settlementUnitPrice())
                .append(',')
                .append(row.settlementTotalPrice() == null ? "" : row.settlementTotalPrice())
                .append(',')
                .append(escape(row.batchNo()))
                .append(',')
                .append(escape(row.remark()))
                .append(',')
                .append(row.prescriptionCreatedAt() == null ? "" : row.prescriptionCreatedAt())
                .append('\n'));
        return csv.toString();
    }

    public List<ReportRecords.AuditPerformanceDetail> auditPerformanceDetails(Instant from, Instant to) {
        validateTimeRange(from, to);
        return repository.loadAuditPerformanceDetails(from, to);
    }

    public String exportAuditPerformanceDetailsCsv(Instant from, Instant to) {
        List<ReportRecords.AuditPerformanceDetail> rows = auditPerformanceDetails(from, to);
        StringBuilder csv = new StringBuilder("auditor,auditResult,orderNo,externalOrderNo,institutionName,patientName,prescriptionCount,doseCount,reviewComment,auditedAt\n");
        rows.forEach(row -> csv.append(escape(row.auditor()))
                .append(',')
                .append(escape(row.auditResult()))
                .append(',')
                .append(escape(row.orderNo()))
                .append(',')
                .append(escape(row.externalOrderNo()))
                .append(',')
                .append(escape(row.institutionName()))
                .append(',')
                .append(escape(row.patientName()))
                .append(',')
                .append(row.prescriptionCount())
                .append(',')
                .append(row.doseCount())
                .append(',')
                .append(escape(row.reviewComment()))
                .append(',')
                .append(row.auditedAt() == null ? "" : row.auditedAt())
                .append('\n'));
        return csv.toString();
    }

    public List<ReportRecords.DispensePerformanceDetail> dispensePerformanceDetails(Instant from, Instant to) {
        validateTimeRange(from, to);
        return repository.loadDispensePerformanceDetails(from, to);
    }

    public String exportDispensePerformanceDetailsCsv(Instant from, Instant to) {
        List<ReportRecords.DispensePerformanceDetail> rows = dispensePerformanceDetails(from, to);
        StringBuilder csv = new StringBuilder("dispenser,orderNo,externalOrderNo,institutionName,patientName,prescriptionCount,doseCount,printStatus,dispenseComment,dispensedAt\n");
        rows.forEach(row -> csv.append(escape(row.dispenser()))
                .append(',')
                .append(escape(row.orderNo()))
                .append(',')
                .append(escape(row.externalOrderNo()))
                .append(',')
                .append(escape(row.institutionName()))
                .append(',')
                .append(escape(row.patientName()))
                .append(',')
                .append(row.prescriptionCount())
                .append(',')
                .append(row.doseCount())
                .append(',')
                .append(escape(row.printStatus()))
                .append(',')
                .append(escape(row.dispenseComment()))
                .append(',')
                .append(row.dispensedAt() == null ? "" : row.dispensedAt())
                .append('\n'));
        return csv.toString();
    }

    public List<ReportRecords.RecheckPerformanceDetail> recheckPerformanceDetails(Instant from, Instant to) {
        validateTimeRange(from, to);
        return repository.loadRecheckPerformanceDetails(from, to);
    }

    public String exportRecheckPerformanceDetailsCsv(Instant from, Instant to) {
        List<ReportRecords.RecheckPerformanceDetail> rows = recheckPerformanceDetails(from, to);
        StringBuilder csv = new StringBuilder("rechecker,recheckResult,orderNo,externalOrderNo,institutionName,patientName,prescriptionCount,doseCount,recheckComment,recheckedAt\n");
        rows.forEach(row -> csv.append(escape(row.rechecker()))
                .append(',')
                .append(escape(row.recheckResult()))
                .append(',')
                .append(escape(row.orderNo()))
                .append(',')
                .append(escape(row.externalOrderNo()))
                .append(',')
                .append(escape(row.institutionName()))
                .append(',')
                .append(escape(row.patientName()))
                .append(',')
                .append(row.prescriptionCount())
                .append(',')
                .append(row.doseCount())
                .append(',')
                .append(escape(row.recheckComment()))
                .append(',')
                .append(row.recheckedAt() == null ? "" : row.recheckedAt())
                .append('\n'));
        return csv.toString();
    }

    public List<ReportRecords.DecoctionPerformanceDetail> decoctionPerformanceDetails(Instant from, Instant to) {
        validateTimeRange(from, to);
        return repository.loadDecoctionPerformanceDetails(from, to);
    }

    public String exportDecoctionPerformanceDetailsCsv(Instant from, Instant to) {
        List<ReportRecords.DecoctionPerformanceDetail> rows = decoctionPerformanceDetails(from, to);
        StringBuilder csv = new StringBuilder("operator,orderNo,externalOrderNo,institutionName,patientName,taskNo,prescriptionNo,deviceCode,pailNo,actionType,actionResult,taskStatusBefore,taskStatusAfter,doseCount,source,actionTime\n");
        rows.forEach(row -> csv.append(escape(row.operator()))
                .append(',')
                .append(escape(row.orderNo()))
                .append(',')
                .append(escape(row.externalOrderNo()))
                .append(',')
                .append(escape(row.institutionName()))
                .append(',')
                .append(escape(row.patientName()))
                .append(',')
                .append(escape(row.taskNo()))
                .append(',')
                .append(escape(row.prescriptionNo()))
                .append(',')
                .append(escape(row.deviceCode()))
                .append(',')
                .append(escape(row.pailNo()))
                .append(',')
                .append(escape(row.actionType()))
                .append(',')
                .append(escape(row.actionResult()))
                .append(',')
                .append(escape(row.taskStatusBefore()))
                .append(',')
                .append(escape(row.taskStatusAfter()))
                .append(',')
                .append(row.doseCount())
                .append(',')
                .append(escape(row.source()))
                .append(',')
                .append(row.actionTime() == null ? "" : row.actionTime())
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
