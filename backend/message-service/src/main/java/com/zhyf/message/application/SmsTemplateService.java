package com.zhyf.message.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.message.infrastructure.SmsTemplateRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SmsTemplateService {

    private static final UUID DEFAULT_ADMIN_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final SmsTemplateRepository repository;

    public SmsTemplateService(SmsTemplateRepository repository) {
        this.repository = repository;
    }

    public SmsTemplateRecords.SmsTemplatePage listSmsTemplates(SmsTemplateRecords.SmsTemplateQuery query) {
        SmsTemplateRecords.SmsTemplateQuery currentQuery = query == null
                ? new SmsTemplateRecords.SmsTemplateQuery(null, null, null, 1, 20)
                : query;
        int page = Math.max(currentQuery.page(), 1);
        int pageSize = Math.min(Math.max(currentQuery.pageSize(), 1), 100);
        return repository.searchSmsTemplates(new SmsTemplateRecords.SmsTemplateQuery(
                cleanText(currentQuery.keyword()),
                cleanText(currentQuery.templateType()),
                currentQuery.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public SmsTemplateRecords.SmsTemplateRecord createSmsTemplate(SmsTemplateRecords.SmsTemplateCommand command) {
        requireCommand(command);
        String templateCode = requireText(command.templateCode(), "SMS_TEMPLATE_CODE_REQUIRED", "Template code is required");
        if (repository.findByCode(DEFAULT_ADMIN_TENANT_ID, templateCode).isPresent()) {
            throw new BusinessException("SMS_TEMPLATE_CODE_DUPLICATED", "Template code already exists");
        }
        return repository.insertSmsTemplate(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                templateCode,
                requireText(command.templateName(), "SMS_TEMPLATE_NAME_REQUIRED", "Template name is required"),
                defaultText(command.templateType(), "ORDER"),
                requireText(command.contentTemplate(), "SMS_TEMPLATE_CONTENT_REQUIRED", "Template content is required"),
                cleanText(command.signature()),
                command.enabled() == null || command.enabled()
        );
    }

    @Transactional
    public SmsTemplateRecords.SmsTemplateRecord updateSmsTemplate(
            UUID templateId,
            SmsTemplateRecords.SmsTemplateCommand command
    ) {
        requireCommand(command);
        SmsTemplateRecords.SmsTemplateRecord existing = repository.findById(templateId)
                .orElseThrow(() -> new BusinessException(
                        "SMS_TEMPLATE_NOT_FOUND",
                        "SMS template not found"
                ));
        return repository.updateSmsTemplate(
                existing.id(),
                requireText(command.templateName(), "SMS_TEMPLATE_NAME_REQUIRED", "Template name is required"),
                defaultText(command.templateType(), existing.templateType()),
                requireText(command.contentTemplate(), "SMS_TEMPLATE_CONTENT_REQUIRED", "Template content is required"),
                cleanText(command.signature()),
                command.enabled() == null ? existing.enabled() : command.enabled()
        );
    }

    private void requireCommand(SmsTemplateRecords.SmsTemplateCommand command) {
        if (command == null) {
            throw new BusinessException("SMS_TEMPLATE_COMMAND_REQUIRED", "Sms template command is required");
        }
    }

    private String requireText(String value, String code, String message) {
        String cleaned = cleanText(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new BusinessException(code, message);
        }
        return cleaned;
    }

    private String defaultText(String value, String fallback) {
        String cleaned = cleanText(value);
        return StringUtils.hasText(cleaned) ? cleaned : fallback;
    }

    private String cleanText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
