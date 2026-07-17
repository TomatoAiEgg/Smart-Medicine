package com.zhyf.ops.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.ops.application.OpsQueryService;
import com.zhyf.ops.application.OpsRecords;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ops")
public class OpsQueryController {

    private final OpsQueryService queryService;

    public OpsQueryController(OpsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/outbox")
    public ApiResponse<List<OpsRecords.EventOutboxRecord>> listOutbox(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(queryService.listOutbox(status, eventType, limit));
    }

    @GetMapping("/message-consume-logs")
    public ApiResponse<List<OpsRecords.MessageConsumeRecord>> listMessageConsumeLogs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String consumerGroup,
            @RequestParam(required = false) String eventId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(queryService.listMessageConsumeLogs(status, consumerGroup, eventId, limit));
    }

    @GetMapping("/order-validation-records")
    public ApiResponse<List<OpsRecords.OrderValidationRecord>> listOrderValidationRecords(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) String validationStatus,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(queryService.listOrderValidationRecords(orderId, validationStatus, limit));
    }

    @GetMapping("/api-access-logs")
    public ApiResponse<List<OpsRecords.ApiAccessLogRecord>> listApiAccessLogs(
            @RequestParam(required = false) String appKey,
            @RequestParam(required = false) String resultCode,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(queryService.listApiAccessLogs(appKey, resultCode, limit));
    }

    @GetMapping("/logistics-callback-issues")
    public ApiResponse<List<OpsRecords.LogisticsCallbackIssueRecord>> listLogisticsCallbackIssues(
            @RequestParam(required = false) String callbackStatus,
            @RequestParam(required = false) String callbackType,
            @RequestParam(required = false) String businessId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(queryService.listLogisticsCallbackIssues(
                callbackStatus,
                callbackType,
                businessId,
                orderNo,
                limit
        ));
    }

    @GetMapping("/integration-retry-issues")
    public ApiResponse<List<OpsRecords.IntegrationRetryIssueRecord>> listIntegrationRetryIssues(
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String businessKey,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(queryService.listIntegrationRetryIssues(
                taskStatus,
                taskType,
                businessKey,
                sourceSystem,
                limit
        ));
    }

    @GetMapping("/health-overview")
    public ApiResponse<OpsRecords.OpsHealthOverview> healthOverview(
            @RequestParam(defaultValue = "24") int recentHours
    ) {
        return ApiResponse.ok(queryService.healthOverview(recentHours));
    }
}
