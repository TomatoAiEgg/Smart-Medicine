package com.zhyf.order.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderProgressSnapshot(
        UUID orderId,
        UUID tenantId,
        String orderNo,
        String externalOrderNo,
        String orderStatus,
        Instant createdAt,
        Instant updatedAt,
        List<PrescriptionProgress> prescriptions,
        List<WorkflowProgress> workflowTasks,
        List<DispenseProgress> dispenseRecords,
        List<DecoctionProgress> decoctionTasks,
        List<ShipmentProgress> shipments,
        List<CallbackProgress> callbacks,
        List<StatusLogProgress> statusLogs
) {

    public record PrescriptionProgress(
            UUID prescriptionId,
            String prescriptionNo,
            String externalPrescriptionNo,
            String prescriptionStatus,
            int detailCount,
            Instant createdAt
    ) {
    }

    public record WorkflowProgress(
            UUID taskId,
            String taskType,
            String taskStatus,
            String operator,
            String comment,
            Instant createdAt,
            Instant completedAt
    ) {
    }

    public record DispenseProgress(
            UUID recordId,
            UUID taskId,
            String dispenser,
            String dispenseComment,
            String printStatus,
            Instant dispensedAt
    ) {
    }

    public record DecoctionProgress(
            UUID taskId,
            String taskNo,
            String prescriptionNo,
            String deviceCode,
            String pailNo,
            String taskStatus,
            String operator,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt
    ) {
    }

    public record ShipmentProgress(
            UUID shipmentId,
            String logisticsNo,
            String logisticsCompany,
            String logisticsStatus,
            String latestTraceStatus,
            String latestTraceContent,
            Instant latestTraceTime
    ) {
    }

    public record CallbackProgress(
            UUID callbackId,
            String callbackType,
            String businessId,
            String callbackStatus,
            int retryCount,
            Instant nextRetryAt,
            Instant updatedAt
    ) {
    }

    public record StatusLogProgress(
            UUID logId,
            String fromStatus,
            String toStatus,
            String operatorType,
            String source,
            Instant createdAt
    ) {
    }
}
