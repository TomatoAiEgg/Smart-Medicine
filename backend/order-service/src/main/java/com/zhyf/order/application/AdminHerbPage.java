package com.zhyf.order.application;

import java.util.List;

public record AdminHerbPage(
        List<AdminHerbRecord> records,
        long total,
        int page,
        int pageSize
) {
}
