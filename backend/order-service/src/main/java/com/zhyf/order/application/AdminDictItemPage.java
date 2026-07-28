package com.zhyf.order.application;

import java.util.List;

public record AdminDictItemPage(
        List<AdminDictItemRecord> records,
        long total,
        int page,
        int pageSize
) {
}
