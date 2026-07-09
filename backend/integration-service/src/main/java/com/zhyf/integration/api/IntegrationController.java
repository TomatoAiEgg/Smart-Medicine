package com.zhyf.integration.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.integration.application.IntegrationCommands;
import com.zhyf.integration.application.IntegrationRecords;
import com.zhyf.integration.application.IntegrationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration")
public class IntegrationController {

    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @PostMapping("/community/messages")
    public ApiResponse<IntegrationRecords.IntegrationMessageRecord> recordCommunityMessage(
            @RequestBody IntegrationCommands.CommunityMessageCommand command
    ) {
        return ApiResponse.ok(integrationService.recordCommunityMessage(command));
    }

    @PostMapping("/address/push-records")
    public ApiResponse<IntegrationRecords.IntegrationMessageRecord> recordAddressPush(
            @RequestBody IntegrationCommands.AddressPushCommand command
    ) {
        return ApiResponse.ok(integrationService.recordAddressPush(command));
    }

    @GetMapping("/hospital/prescriptions/{prescriptionNo}/order")
    public ApiResponse<IntegrationRecords.HospitalOrderRecord> findHospitalOrderByPrescription(
            @PathVariable String prescriptionNo,
            @RequestParam String phone
    ) {
        return ApiResponse.ok(integrationService.findHospitalOrderByPrescription(prescriptionNo, phone));
    }
}
