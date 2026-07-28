package com.zhyf.order.application;

import java.util.List;

public record AdminHerbAreaPage(
        List<AdminHerbAreaRecord> records,
        long total,
        int page,
        int pageSize
) {
}
