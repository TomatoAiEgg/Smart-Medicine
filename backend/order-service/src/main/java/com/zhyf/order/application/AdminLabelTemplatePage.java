package com.zhyf.order.application;

import java.util.List;

public record AdminLabelTemplatePage(
        List<AdminLabelTemplateRecord> records,
        long total,
        int page,
        int pageSize
) {
}
