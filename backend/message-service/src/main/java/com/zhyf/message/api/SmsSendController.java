package com.zhyf.message.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.message.application.SmsSendRecords;
import com.zhyf.message.application.SmsSendService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sms")
public class SmsSendController {

    private final SmsSendService smsSendService;

    public SmsSendController(SmsSendService smsSendService) {
        this.smsSendService = smsSendService;
    }

    @PostMapping("/send-single")
    public ApiResponse<SmsSendRecords.SmsSendResult> sendSingleSms(
            @RequestBody SmsSendRecords.SmsSendCommand command
    ) {
        return ApiResponse.ok(smsSendService.sendSingleSms(command));
    }

    @GetMapping("/records")
    public ApiResponse<SmsSendRecords.SmsRecordPage> listSmsRecords(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sendStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(smsSendService.listSmsRecords(new SmsSendRecords.SmsRecordQuery(
                keyword,
                sendStatus,
                page,
                pageSize
        )));
    }
}
