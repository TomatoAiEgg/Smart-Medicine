package com.zhyf.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.workflow.infrastructure.OrderValidationRecordRepository;
import com.zhyf.workflow.infrastructure.WorkflowTaskRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Service
public class OrderCreatedWorkflowService {

    private static final int DEFAULT_VALIDATION_LOOKUP_ATTEMPTS = 10;
    private static final long DEFAULT_VALIDATION_LOOKUP_DELAY_MILLIS = 300L;

    private final ObjectMapper objectMapper;
    private final OrderValidationRecordRepository validationRecordRepository;
    private final WorkflowTaskRepository taskRepository;
    private final int validationLookupAttempts;
    private final long validationLookupDelayMillis;

    @Autowired
    public OrderCreatedWorkflowService(
            ObjectMapper objectMapper,
            OrderValidationRecordRepository validationRecordRepository,
            WorkflowTaskRepository taskRepository
    ) {
        this(
                objectMapper,
                validationRecordRepository,
                taskRepository,
                DEFAULT_VALIDATION_LOOKUP_ATTEMPTS,
                DEFAULT_VALIDATION_LOOKUP_DELAY_MILLIS
        );
    }

    OrderCreatedWorkflowService(
            ObjectMapper objectMapper,
            OrderValidationRecordRepository validationRecordRepository,
            WorkflowTaskRepository taskRepository,
            int validationLookupAttempts,
            long validationLookupDelayMillis
    ) {
        this.objectMapper = objectMapper;
        this.validationRecordRepository = validationRecordRepository;
        this.taskRepository = taskRepository;
        this.validationLookupAttempts = validationLookupAttempts;
        this.validationLookupDelayMillis = validationLookupDelayMillis;
    }

    public void createReviewTaskIfValidationPassed(String eventId, String aggregateId, String payload) {
        createReviewTaskIfValidationPassed("ORDER_CREATED", eventId, aggregateId, payload);
    }

    public void createReviewTaskIfValidationPassed(String eventType, String eventId, String aggregateId, String payload) {
        Assert.isTrue(StringUtils.hasText(eventId), "eventId is required");
        Assert.isTrue(StringUtils.hasText(aggregateId), "aggregateId is required");
        UUID orderId = UUID.fromString(aggregateId);
        OrderValidationRecordRepository.OrderValidationRecord validation = findValidationRecord(eventId, orderId);
        if (!"PASSED".equals(validation.validationStatus())) {
            return;
        }
        JsonNode payloadNode;
        try {
            payloadNode = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("ORDER_CREATED payload parse failed, eventId=" + eventId, ex);
        }
        String payloadOrderId = text(payloadNode, "orderId");
        if (!orderId.toString().equals(payloadOrderId)) {
            throw new IllegalStateException(eventType + " orderId mismatch, eventId=" + eventId);
        }
        if (isPrescriptionUpdatedEvent(eventType)) {
            taskRepository.cancelPendingReviewTasksByOrderId(
                    orderId,
                    "workflow-service",
                    "处方修改后重新生成审方任务"
            );
            taskRepository.cancelPendingDownstreamTasksByOrderId(
                    orderId,
                    "workflow-service",
                    "处方修改后废弃旧处方待办"
            );
        }
        taskRepository.createOrderReviewTask(
                UUID.randomUUID(),
                validation.tenantId(),
                orderId,
                eventId,
                payload
        );
    }

    private boolean isPrescriptionUpdatedEvent(String eventType) {
        return "ORDER_PRESCRIPTION_UPDATED".equalsIgnoreCase(eventType);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private OrderValidationRecordRepository.OrderValidationRecord findValidationRecord(String eventId, UUID orderId) {
        int attempts = Math.max(validationLookupAttempts, 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            var record = validationRecordRepository.findByEventId(eventId);
            if (record.isPresent()) {
                OrderValidationRecordRepository.OrderValidationRecord validation = record.get();
                if (!orderId.equals(validation.orderId())) {
                    throw new IllegalStateException("order validation record orderId mismatch, eventId=" + eventId);
                }
                return validation;
            }
            if (attempt < attempts) {
                sleepBeforeRetry(orderId);
            }
        }
        throw new IllegalStateException("order validation record not found, orderId=" + orderId);
    }

    private void sleepBeforeRetry(UUID orderId) {
        if (validationLookupDelayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(validationLookupDelayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for order validation record, orderId=" + orderId, ex);
        }
    }
}
