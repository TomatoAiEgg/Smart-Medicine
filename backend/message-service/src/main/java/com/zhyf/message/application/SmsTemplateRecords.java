package com.zhyf.message.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SmsTemplateRecords {

    private SmsTemplateRecords() {
    }

    public record SmsTemplateQuery(
            String keyword,
            String templateType,
            Boolean enabled,
            int page,
            int pageSize
    ) {
    }

    public record SmsTemplateRecord(
            UUID id,
            UUID tenantId,
            String templateCode,
            String templateName,
            String templateType,
            String contentTemplate,
            String signature,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record SmsTemplatePage(
            List<SmsTemplateRecord> records,
            long total,
            int page,
            int pageSize
    ) {
    }

    public record SmsTemplateCommand(
            String templateCode,
            String templateName,
            String templateType,
            String contentTemplate,
            String signature,
            Boolean enabled
    ) {
    }
}
