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
        Assert.isTrue(StringUtils.hasText(eventId), "eventId is required");
        Assert.isTrue(StringUtils.hasText(aggregateId), "aggregateId is required");
        UUID orderId = UUID.fromString(aggregateId);
        OrderValidationRecordRepository.OrderValidationRecord validation = findValidationRecord(orderId);
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
            throw new IllegalStateException("ORDER_CREATED orderId mismatch, eventId=" + eventId);
        }
        taskRepository.createOrderReviewTask(
                UUID.randomUUID(),
                validation.tenantId(),
                orderId,
                eventId,
                payload
        );
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private OrderValidationRecordRepository.OrderValidationRecord findValidationRecord(UUID orderId) {
        int attempts = Math.max(validationLookupAttempts, 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            var record = validationRecordRepository.findLatestByOrderId(orderId);
            if (record.isPresent()) {
                return record.get();
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
