package com.zhyf.order.application;

import java.util.List;

public record AdminPrescriptionReprintPage(
        List<AdminPrescriptionReprintItem> records,
        long total,
        int page,
        int pageSize
) {
}
