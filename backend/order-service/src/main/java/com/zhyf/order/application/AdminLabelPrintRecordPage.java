package com.zhyf.order.application;

import java.util.List;

public record AdminLabelPrintRecordPage(
        List<AdminLabelPrintRecord> records,
        long total,
        int page,
        int pageSize
) {
}
