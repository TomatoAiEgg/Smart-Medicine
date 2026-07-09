package com.zhyf.integration.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.integration.application.IntegrationRecords;
import com.zhyf.integration.application.IntegrationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/integration")
public class IntegrationAdminController {

    private final IntegrationService integrationService;

    public IntegrationAdminController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping("/messages")
    public ApiResponse<List<IntegrationRecords.IntegrationMessageRecord>> listMessages(
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String processStatus,
            @RequestParam(required = false) String businessKey,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(integrationService.listMessages(sourceType, processStatus, businessKey, limit));
    }
}
