package com.zhyf.message.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.message.infrastructure.OrderValidationRecordRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Component
public class OrderCreatedEventHandler implements MessageEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedEventHandler.class);
    private final ObjectMapper objectMapper;
    private final OrderValidationRecordRepository recordRepository;

    public OrderCreatedEventHandler(
            ObjectMapper objectMapper,
            OrderValidationRecordRepository recordRepository
    ) {
        this.objectMapper = objectMapper;
        this.recordRepository = recordRepository;
    }

    @Override
    public boolean supports(String eventType) {
        return "ORDER_CREATED".equalsIgnoreCase(eventType);
    }

    @Override
    public void handle(MessageEvent event) {
        Assert.isTrue(StringUtils.hasText(event.eventId()), "eventId is required");
        Assert.isTrue(StringUtils.hasText(event.aggregateId()), "aggregateId is required");
        try {
            JsonNode payload = objectMapper.readTree(event.payload());
            String tenantId = text(payload, "tenantId");
            String payloadOrderId = text(payload, "orderId");
            String orderNo = text(payload, "orderNo");
            String externalOrderNo = text(payload, "externalOrderNo");
            JsonNode prescriptionIds = payload.get("prescriptionIds");
            UUID resolvedTenantId = parseUuid(tenantId, "tenantId");
            UUID resolvedOrderId = parseUuid(event.aggregateId(), "aggregateId");
            List<String> errors = validate(tenantId, payloadOrderId, resolvedOrderId, orderNo, externalOrderNo, prescriptionIds);
            String status = errors.isEmpty() ? "PASSED" : "REJECTED";
            String message = errors.isEmpty() ? "基础校验通过" : String.join("；", errors);

            recordRepository.insert(
                    UUID.randomUUID(),
                    resolvedTenantId,
                    resolvedOrderId,
                    event.eventId(),
                    status,
                    message,
                    event.payload()
            );
            log.info("handled order created event eventId={} messageId={} aggregateId={} validationStatus={}",
                    event.eventId(), event.messageId(), event.aggregateId(), status);
        } catch (Exception ex) {
            throw new IllegalStateException("order created event handling failed, eventId=" + event.eventId(), ex);
        }
    }

    private List<String> validate(
            String tenantId,
            String payloadOrderId,
            UUID aggregateOrderId,
            String orderNo,
            String externalOrderNo,
            JsonNode prescriptionIds
    ) {
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasText(tenantId)) {
            errors.add("tenantId 缺失");
        }
        if (!StringUtils.hasText(payloadOrderId)) {
            errors.add("orderId 缺失");
        } else {
            try {
                UUID parsedOrderId = UUID.fromString(payloadOrderId);
                if (!parsedOrderId.equals(aggregateOrderId)) {
                    errors.add("orderId 与 aggregateId 不一致");
                }
            } catch (IllegalArgumentException ex) {
                errors.add("orderId 格式错误");
            }
        }
        if (!StringUtils.hasText(orderNo)) {
            errors.add("orderNo 缺失");
        }
        if (!StringUtils.hasText(externalOrderNo)) {
            errors.add("externalOrderNo 缺失");
        }
        if (prescriptionIds == null || !prescriptionIds.isArray() || prescriptionIds.isEmpty()) {
            errors.add("prescriptionIds 缺失");
        }
        return errors;
    }

    private UUID parseUuid(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return UUID.fromString(value);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
