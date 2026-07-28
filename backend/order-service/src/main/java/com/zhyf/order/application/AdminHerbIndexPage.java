package com.zhyf.order.application;

import java.util.List;

public record AdminHerbIndexPage(
        List<AdminHerbIndexRecord> records,
        long total,
        int page,
        int pageSize
) {
}
