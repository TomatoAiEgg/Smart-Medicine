package com.zhyf.message.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.message.infrastructure.SmsSendRecordRepository;
import com.zhyf.message.infrastructure.SmsTemplateRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SmsSendService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{6,20}$");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");
    private static final String SIMULATED_STATUS = "SIMULATED";

    private final SmsTemplateRepository templateRepository;
    private final SmsSendRecordRepository sendRecordRepository;

    public SmsSendService(
            SmsTemplateRepository templateRepository,
            SmsSendRecordRepository sendRecordRepository
    ) {
        this.templateRepository = templateRepository;
        this.sendRecordRepository = sendRecordRepository;
    }

    @Transactional
    public SmsSendRecords.SmsSendResult sendSingleSms(SmsSendRecords.SmsSendCommand command) {
        requireCommand(command);
        SmsTemplateRecords.SmsTemplateRecord template = templateRepository.findById(command.templateId())
                .orElseThrow(() -> new BusinessException("SMS_TEMPLATE_NOT_FOUND", "Sms template not found"));
        if (!template.enabled()) {
            throw new BusinessException("SMS_TEMPLATE_DISABLED", "Sms template is disabled");
        }
        String receiverPhone = requirePhone(command.receiverPhone());
        Map<String, String> variables = normalizeVariables(command.variables());
        String content = renderTemplate(template.contentTemplate(), variables);
        return sendRecordRepository.insertSingleSendRecord(
                UUID.randomUUID(),
                template,
                receiverPhone,
                cleanText(command.receiverName()),
                cleanText(command.relatedOrderNo()),
                content,
                variables,
                SIMULATED_STATUS,
                defaultText(command.operator(), "admin")
        );
    }

    public SmsSendRecords.SmsRecordPage listSmsRecords(SmsSendRecords.SmsRecordQuery query) {
        SmsSendRecords.SmsRecordQuery currentQuery = query == null
                ? new SmsSendRecords.SmsRecordQuery(null, null, 1, 20)
                : query;
        int page = Math.max(currentQuery.page(), 1);
        int pageSize = Math.min(Math.max(currentQuery.pageSize(), 1), 100);
        return sendRecordRepository.searchSmsRecords(new SmsSendRecords.SmsRecordQuery(
                cleanText(currentQuery.keyword()),
                cleanText(currentQuery.sendStatus()),
                page,
                pageSize
        ));
    }

    private void requireCommand(SmsSendRecords.SmsSendCommand command) {
        if (command == null || command.templateId() == null) {
            throw new BusinessException("SMS_SEND_COMMAND_REQUIRED", "Sms send command is required");
        }
    }

    private String requirePhone(String value) {
        String phone = requireText(value, "SMS_RECEIVER_PHONE_REQUIRED", "Receiver phone is required");
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException("SMS_RECEIVER_PHONE_INVALID", "Receiver phone is invalid");
        }
        return phone;
    }

    private Map<String, String> normalizeVariables(Map<String, String> variables) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (variables == null) {
            return normalized;
        }
        variables.forEach((key, value) -> {
            String cleanedKey = cleanText(key);
            if (cleanedKey != null) {
                normalized.put(cleanedKey, cleanText(value) == null ? "" : cleanText(value));
            }
        });
        return normalized;
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = variables.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
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
