package com.zhyf.order.application;

public record AdminLabelPrintRecordQuery(
        String printStatus,
        String prescriptionNo,
        int page,
        int pageSize
) {
}
