package com.zhyf.report.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReportRecords {

    private ReportRecords() {
    }

    public record ReportOverview(
            Instant from,
            Instant to,
            int trendDays,
            long totalOrders,
            long totalPrescriptions,
            long totalShipments,
            long totalCallbacks,
            long pendingAddressSupplements,
            List<StatusCount> orderStatusCounts,
            List<StatusCount> callbackStatusCounts,
            List<DailyOrderCount> dailyOrderCounts
    ) {
    }

    public record StatusCount(String status, long count) {
    }

    public record DailyOrderCount(LocalDate day, long count) {
    }

    public record InstitutionPrescriptionCount(
            String institutionId,
            String institutionCode,
            String institutionName,
            long orderCount,
            long prescriptionCount,
            long doseCount,
            BigDecimal totalAmount
    ) {
    }

    public record DispensePerformance(
            String dispenser,
            long dispenseCount,
            long orderCount,
            long prescriptionCount,
            long doseCount,
            Instant firstDispensedAt,
            Instant lastDispensedAt
    ) {
    }

    public record RecheckPerformance(
            String rechecker,
            long recheckCount,
            long orderCount,
            long prescriptionCount,
            long doseCount,
            Instant firstRecheckedAt,
            Instant lastRecheckedAt
    ) {
    }

    public record AuditPerformance(
            String auditor,
            long auditCount,
            long approvedCount,
            long rejectedCount,
            long orderCount,
            long prescriptionCount,
            long doseCount,
            Instant firstAuditedAt,
            Instant lastAuditedAt
    ) {
    }

    public record DecoctionPerformance(
            String operator,
            long decoctionCount,
            long orderCount,
            long prescriptionCount,
            long doseCount,
            long deviceCount,
            Instant firstFinishedAt,
            Instant lastFinishedAt
    ) {
    }

    public record HerbDosage(
            String herbCode,
            String herbName,
            String drugSpecs,
            String drugOrigin,
            String unit,
            long detailCount,
            long prescriptionCount,
            long orderCount,
            BigDecimal totalQuantity,
            BigDecimal totalAmount,
            BigDecimal settlementAmount
    ) {
    }

    public record InstitutionHerbReconciliation(
            String institutionId,
            String institutionCode,
            String institutionName,
            String herbCode,
            String herbName,
            String drugSpecs,
            String drugOrigin,
            String unit,
            long detailCount,
            long prescriptionCount,
            long orderCount,
            BigDecimal totalQuantity,
            BigDecimal totalAmount,
            BigDecimal settlementAmount
    ) {
    }

    public record PrescriptionHerbDetail(
            String institutionCode,
            String institutionName,
            String orderNo,
            String externalOrderNo,
            String prescriptionNo,
            String externalPrescriptionNo,
            String herbCode,
            String herbName,
            String drugSpecs,
            String drugOrigin,
            String dose,
            String unit,
            String specialUsage,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            BigDecimal settlementUnitPrice,
            BigDecimal settlementTotalPrice,
            String batchNo,
            String remark,
            Instant prescriptionCreatedAt
    ) {
    }
}
