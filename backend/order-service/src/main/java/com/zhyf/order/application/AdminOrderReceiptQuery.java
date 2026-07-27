package com.zhyf.order.application;

public record AdminOrderReceiptQuery(
        String prescriptionNo,
        String receiverName,
        String receiverPhone,
        String patientName,
        int page,
        int pageSize
) {
}
