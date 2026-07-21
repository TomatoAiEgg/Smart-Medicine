package com.zhyf.ops.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.ops.infrastructure.OpsQueryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OpsQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int DEFAULT_HEALTH_HOURS = 24;
    private static final int MAX_HEALTH_HOURS = 168;

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

    public List<OpsRecords.DeadLetterRecord> listDeadLetters(String status, String topic, String eventId, int limit) {
        return repository.findDeadLetters(status, topic, eventId, normalizeLimit(limit));
    }

    @Transactional
    public OpsRecords.DeadLetterOperationResult replayDeadLetter(UUID id, String operator, String remark) {
        OpsRecords.DeadLetterRecord record = findDeadLetter(id);
        if (!"OPEN".equals(record.status())) {
            throw new BusinessException("DEAD_LETTER_STATUS_INVALID", "Only OPEN dead letter can be replayed");
        }
        int outboxResetCount = repository.resetDeadLetterForReplay(id);
        if (outboxResetCount == 0) {
            throw new BusinessException("DEAD_LETTER_OUTBOX_NOT_FOUND", "Outbox event not found for replay");
        }
        int updated = repository.markDeadLetterReplayed(
                id,
                normalizedOperator(operator),
                normalizedRemark(remark, "manual replay")
        );
        if (updated == 0) {
            throw new BusinessException("DEAD_LETTER_STATUS_INVALID", "Dead letter status changed");
        }
        return new OpsRecords.DeadLetterOperationResult(
                record.id(),
                record.eventId(),
                "REPLAYED",
                outboxResetCount,
                "Dead letter replay submitted"
        );
    }

    @Transactional
    public OpsRecords.DeadLetterOperationResult closeDeadLetter(UUID id, String operator, String remark) {
        OpsRecords.DeadLetterRecord record = findDeadLetter(id);
        if (!"OPEN".equals(record.status())) {
            throw new BusinessException("DEAD_LETTER_STATUS_INVALID", "Only OPEN dead letter can be closed");
        }
        int updated = repository.closeDeadLetter(
                id,
                normalizedOperator(operator),
                normalizedRemark(remark, "manual close")
        );
        if (updated == 0) {
            throw new BusinessException("DEAD_LETTER_STATUS_INVALID", "Dead letter status changed");
        }
        return new OpsRecords.DeadLetterOperationResult(
                record.id(),
                record.eventId(),
                "CLOSED",
                0,
                "Dead letter closed"
        );
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

    public OpsRecords.OpsHealthOverview healthOverview(int recentHours) {
        return repository.loadHealthOverview(normalizeHealthHours(recentHours));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeHealthHours(int recentHours) {
        if (recentHours <= 0) {
            return DEFAULT_HEALTH_HOURS;
        }
        return Math.min(recentHours, MAX_HEALTH_HOURS);
    }

    private OpsRecords.DeadLetterRecord findDeadLetter(UUID id) {
        return repository.findDeadLetterById(id).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("DEAD_LETTER_NOT_FOUND", "Dead letter not found"));
    }

    private String normalizedOperator(String operator) {
        return StringUtils.hasText(operator) ? operator.trim() : "admin-console";
    }

    private String normalizedRemark(String remark, String defaultRemark) {
        return StringUtils.hasText(remark) ? remark.trim() : defaultRemark;
    }
}
