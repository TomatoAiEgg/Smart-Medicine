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
}
