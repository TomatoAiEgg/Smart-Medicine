package com.zhyf.message.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.message.application.SmsTemplateRecords;
import com.zhyf.message.application.SmsTemplateService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sms")
public class SmsTemplateController {

    private final SmsTemplateService smsTemplateService;

    public SmsTemplateController(SmsTemplateService smsTemplateService) {
        this.smsTemplateService = smsTemplateService;
    }

    @GetMapping("/templates")
    public ApiResponse<SmsTemplateRecords.SmsTemplatePage> listSmsTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String templateType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(smsTemplateService.listSmsTemplates(new SmsTemplateRecords.SmsTemplateQuery(
                keyword,
                templateType,
                enabled,
                page,
                pageSize
        )));
    }

    @PostMapping("/templates")
    public ApiResponse<SmsTemplateRecords.SmsTemplateRecord> createSmsTemplate(
            @RequestBody SmsTemplateRecords.SmsTemplateCommand command
    ) {
        return ApiResponse.ok(smsTemplateService.createSmsTemplate(command));
    }

    @PatchMapping("/templates/{templateId}")
    public ApiResponse<SmsTemplateRecords.SmsTemplateRecord> updateSmsTemplate(
            @PathVariable UUID templateId,
            @RequestBody SmsTemplateRecords.SmsTemplateCommand command
    ) {
        return ApiResponse.ok(smsTemplateService.updateSmsTemplate(templateId, command));
    }
}
