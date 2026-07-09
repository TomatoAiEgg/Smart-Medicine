package com.zhyf.portal.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PortalRecords {

    private PortalRecords() {
    }

    public record PortalOrderRecord(
            UUID tenantId,
            UUID orderId,
            String institutionName,
            String orderNo,
            String externalOrderNo,
            String orderStatus,
            String patientName,
            String patientPhone,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            Instant createdAt,
            List<PrescriptionRecord> prescriptions,
            ShipmentRecord shipment
    ) {
    }

    public record PrescriptionRecord(
            String prescriptionNo,
            String prescriptionStatus,
            String prescriptionType,
            String doctorName,
            String diagnosis
    ) {
    }

    public record ShipmentRecord(
            String logisticsNo,
            String logisticsCompany,
            String logisticsStatus,
            Instant latestTraceTime
    ) {
    }

    public record AddressSupplementRecord(
            UUID supplementId,
            UUID tenantId,
            UUID orderId,
            String orderNo,
            String supplementStatus,
            String receiverName,
            String receiverPhone,
            String receiverProvince,
            String receiverCity,
            String receiverZone,
            String receiverAddress,
            String requesterName,
            String requesterPhone,
            String remark,
            Instant createdAt
    ) {
    }
}
