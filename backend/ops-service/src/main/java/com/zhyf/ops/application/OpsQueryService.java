package com.zhyf.ops.application;

import com.zhyf.ops.infrastructure.OpsQueryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OpsQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final OpsQueryRepository repository;

    public OpsQueryService(OpsQueryRepository repository) {
        this.repository = repository;
    }

    public List<OpsRecords.EventOutboxRecord> listOutbox(String status, String eventType, int limit) {
        return repository.findEventOutbox(status, eventType, normalizeLimit(limit));
    }

    public List<OpsRecords.MessageConsumeRecord> listMessageConsumeLogs(
            String status,
            String consumerGroup,
            String eventId,
            int limit
    ) {
        return repository.findMessageConsumeLogs(status, consumerGroup, eventId, normalizeLimit(limit));
    }

    public List<OpsRecords.OrderValidationRecord> listOrderValidationRecords(
            UUID orderId,
            String validationStatus,
            int limit
    ) {
        return repository.findOrderValidationRecords(orderId, validationStatus, normalizeLimit(limit));
    }

    public List<OpsRecords.ApiAccessLogRecord> listApiAccessLogs(String appKey, String resultCode, int limit) {
        return repository.findApiAccessLogs(appKey, resultCode, normalizeLimit(limit));
    }

    public List<OpsRecords.LogisticsCallbackIssueRecord> listLogisticsCallbackIssues(
            String callbackStatus,
            String callbackType,
            String businessId,
            String orderNo,
            int limit
    ) {
        return repository.findLogisticsCallbackIssues(
                callbackStatus,
                callbackType,
                businessId,
                orderNo,
                normalizeLimit(limit)
        );
    }

    public List<OpsRecords.IntegrationRetryIssueRecord> listIntegrationRetryIssues(
            String taskStatus,
            String taskType,
            String businessKey,
            String sourceSystem,
            int limit
    ) {
        return repository.findIntegrationRetryIssues(
                taskStatus,
                taskType,
                businessKey,
                sourceSystem,
                normalizeLimit(limit)
        );
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
