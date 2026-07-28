package com.zhyf.message.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.message.application.SmsSendRecords;
import com.zhyf.message.application.SmsSendService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
