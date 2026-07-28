package com.zhyf.ops.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.ops.infrastructure.OpsQueryRepository;
import java.math.BigDecimal;
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

    public List<OpsRecords.ProblemRegistrationRecord> listProblemRegistrations(
            String status,
            String orderNo,
            String keyword,
            int limit
    ) {
        return repository.findProblemRegistrations(status, orderNo, keyword, normalizeLimit(limit));
    }

    @Transactional
    public OpsRecords.ProblemRegistrationRecord createProblemRegistration(
            OpsRecords.ProblemRegistrationCommand command
    ) {
        if (command == null) {
            throw new BusinessException("PROBLEM_REGISTRATION_COMMAND_REQUIRED", "Problem registration command is required");
        }
        String orderNo = requireText(command.orderNo(), "PROBLEM_ORDER_NO_REQUIRED", "Order no is required");
        OpsRecords.OrderIdentityRecord order = repository.findOrderIdentity(orderNo, null)
                .orElseThrow(() -> new BusinessException("PROBLEM_ORDER_NOT_FOUND", "Order not found"));
        UUID id = UUID.randomUUID();
        String operator = normalizedOperator(command.operator());
        OpsRecords.ProblemRegistrationRecord record = repository.insertProblemRegistration(
                id,
                order.tenantId(),
                order.id(),
                order.institutionId(),
                order.orderNo(),
                order.externalOrderNo(),
                defaultText(command.problemType(), "ORDER"),
                requireText(command.problemReason(), "PROBLEM_REASON_REQUIRED", "Problem reason is required"),
                requireText(command.handlingPlan(), "PROBLEM_HANDLING_PLAN_REQUIRED", "Handling plan is required"),
                normalizeAmount(command.amount()),
                operator,
                normalizeText(command.remark())
        );
        repository.insertProblemRegistrationAction(
                UUID.randomUUID(),
                id,
                "CREATE",
                null,
                record.status(),
                operator,
                normalizedRemark(command.remark(), "create problem registration")
        );
        return record;
    }

    @Transactional
    public OpsRecords.ProblemRegistrationRecord updateProblemRegistration(
            UUID id,
            OpsRecords.ProblemRegistrationCommand command
    ) {
        OpsRecords.ProblemRegistrationRecord existing = findProblemRegistration(id);
        String operator = normalizedOperator(command == null ? null : command.operator());
        OpsRecords.ProblemRegistrationRecord updated = repository.updateProblemRegistration(
                id,
                defaultText(command == null ? null : command.problemType(), existing.problemType()),
                requireText(command == null ? null : command.problemReason(), "PROBLEM_REASON_REQUIRED", "Problem reason is required"),
                requireText(command == null ? null : command.handlingPlan(), "PROBLEM_HANDLING_PLAN_REQUIRED", "Handling plan is required"),
                command == null || command.amount() == null ? existing.amount() : normalizeAmount(command.amount()),
                operator,
                normalizeText(command == null ? null : command.remark())
        );
        repository.insertProblemRegistrationAction(
                UUID.randomUUID(),
                id,
                "UPDATE",
                existing.status(),
                existing.status(),
                operator,
                normalizedRemark(command == null ? null : command.remark(), "update problem registration")
        );
        return updated;
    }

    @Transactional
    public OpsRecords.ProblemRegistrationRecord handleProblemRegistration(
            UUID id,
            OpsRecords.ProblemRegistrationHandleCommand command
    ) {
        OpsRecords.ProblemRegistrationRecord existing = findProblemRegistration(id);
        String status = normalizeHandleStatus(command == null ? null : command.status());
        String remark = command == null ? null : command.remark();
        if ("CLOSED".equals(status) && !StringUtils.hasText(remark)) {
            throw new BusinessException("PROBLEM_CLOSE_REASON_REQUIRED", "Close reason is required");
        }
        String operator = normalizedOperator(command == null ? null : command.operator());
        OpsRecords.ProblemRegistrationRecord updated = repository.handleProblemRegistration(
                id,
                status,
                defaultText(command == null ? null : command.handlingPlan(), existing.handlingPlan()),
                command == null || command.amount() == null ? existing.amount() : normalizeAmount(command.amount()),
                operator,
                normalizeText(remark)
        );
        repository.insertProblemRegistrationAction(
                UUID.randomUUID(),
                id,
                "CLOSED".equals(status) ? "CLOSE" : "HANDLE",
                existing.status(),
                status,
                operator,
                normalizedRemark(remark, "handle problem registration")
        );
        return updated;
    }

    public List<OpsRecords.ProblemRegistrationActionRecord> listProblemRegistrationActions(UUID id) {
        findProblemRegistration(id);
        return repository.findProblemRegistrationActions(id, normalizeLimit(DEFAULT_LIMIT));
    }

    public OpsRecords.OpsHealthOverview healthOverview(int recentHours) {
        return repository.loadHealthOverview(normalizeHealthHours(recentHours));
    }

    public OpsRecords.OrderObservabilityBundle loadOrderObservability(
            String orderNo,
            String externalOrderNo,
            int limit
    ) {
        String normalizedOrderNo = normalizeText(orderNo);
        String normalizedExternalOrderNo = normalizeText(externalOrderNo);
        if (!StringUtils.hasText(normalizedOrderNo) && !StringUtils.hasText(normalizedExternalOrderNo)) {
            throw new BusinessException(
                    "ORDER_OBSERVABILITY_KEY_REQUIRED",
                    "orderNo or externalOrderNo is required"
            );
        }
        int normalizedLimit = normalizeLimit(limit);
        OpsRecords.OrderIdentityRecord order = repository.findOrderIdentity(
                normalizedOrderNo,
                normalizedExternalOrderNo
        ).orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found"));
        String aggregateId = order.id().toString();
        List<String> businessKeys = List.of(order.orderNo(), order.externalOrderNo()).stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return new OpsRecords.OrderObservabilityBundle(
                order,
                repository.findOrderStatusLogs(order.id(), normalizedLimit),
                repository.findWorkflowTasks(order.id(), normalizedLimit),
                repository.findEventOutboxByAggregateId(aggregateId, normalizedLimit),
                repository.findMessageConsumeLogsByAggregateId(aggregateId, normalizedLimit),
                repository.findDeadLettersByAggregateId(aggregateId, normalizedLimit),
                repository.findOrderValidationRecords(order.id(), null, normalizedLimit),
                repository.findCallbackRecordsByOrderId(order.id(), normalizedLimit),
                repository.findIntegrationRetriesByBusinessKeys(businessKeys, normalizedLimit),
                repository.findOperationLogsByOrderId(order.id(), normalizedLimit),
                repository.findRecentApiAccessLogsByInstitution(order.institutionId(), normalizedLimit)
        );
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

    private OpsRecords.ProblemRegistrationRecord findProblemRegistration(UUID id) {
        return repository.findProblemRegistrationById(id).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "PROBLEM_REGISTRATION_NOT_FOUND",
                        "Problem registration not found"
                ));
    }

    private String normalizedOperator(String operator) {
        return StringUtils.hasText(operator) ? operator.trim() : "admin-console";
    }

    private String normalizedRemark(String remark, String defaultRemark) {
        return StringUtils.hasText(remark) ? remark.trim() : defaultRemark;
    }

    private String requireText(String value, String code, String message) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(code, message);
        }
        return normalized;
    }

    private String defaultText(String value, String defaultValue) {
        String normalized = normalizeText(value);
        return StringUtils.hasText(normalized) ? normalized : defaultValue;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        BigDecimal normalized = amount == null ? BigDecimal.ZERO : amount;
        if (normalized.signum() < 0) {
            throw new BusinessException("PROBLEM_AMOUNT_INVALID", "Amount cannot be negative");
        }
        return normalized;
    }

    private String normalizeHandleStatus(String status) {
        String normalized = requireText(status, "PROBLEM_STATUS_REQUIRED", "Problem status is required").toUpperCase();
        if (!List.of("PROCESSING", "RESOLVED", "CLOSED").contains(normalized)) {
            throw new BusinessException("PROBLEM_STATUS_INVALID", "Problem status is invalid");
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
