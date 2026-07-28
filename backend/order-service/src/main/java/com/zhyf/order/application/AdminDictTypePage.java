package com.zhyf.order.application;

import java.util.List;

public record AdminDictTypePage(
        List<AdminDictTypeRecord> records,
        long total,
        int page,
        int pageSize
) {
}
