package com.zhyf.callback.application;

import com.zhyf.callback.infrastructure.CallbackRepository;
import com.zhyf.callback.infrastructure.CallbackRepository.OrderCallbackTarget;
import com.zhyf.common.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CallbackService {

    private static final Logger log = LoggerFactory.getLogger(CallbackService.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final CallbackRepository repository;
    private final CallbackSender sender;
    private final CallbackProperties properties;
    private final Clock clock;

    @Autowired
    public CallbackService(CallbackRepository repository, CallbackSender sender, CallbackProperties properties) {
        this(repository, sender, properties, Clock.systemUTC());
    }

    CallbackService(CallbackRepository repository, CallbackSender sender, CallbackProperties properties, Clock clock) {
        this.repository = repository;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public CallbackRecords.CallbackRecord createCallback(CallbackCreateCommand command) {
        if (command.orderId() == null) {
            throw new BusinessException("ORDER_ID_REQUIRED", "Order id is required");
        }
        requireText(command.callbackType(), "CALLBACK_TYPE_REQUIRED", "Callback type is required");
        requireText(command.businessId(), "BUSINESS_ID_REQUIRED", "Business id is required");
        return repository.findByBusinessKey(command.callbackType(), command.businessId())
                .orElseGet(() -> createNewRecord(command));
    }

    public List<CallbackRecords.CallbackRecord> listRecords(String status, String callbackType, UUID orderId, int limit) {
        return repository.findRecords(status, callbackType, orderId, normalizeLimit(limit));
    }

    public int dispatchDueCallbacks(int limit) {
        List<CallbackRecords.CallbackRecord> records = repository.findDueRecords(Instant.now(clock), normalizeLimit(limit));
        for (CallbackRecords.CallbackRecord record : records) {
            dispatchRecord(record);
        }
        return records.size();
    }

    @Transactional
    public CallbackRecords.CallbackRecord markSucceeded(UUID id, String responseBody) {
        int updated = repository.markSucceeded(id, defaultValue(responseBody, "manual success"));
        if (updated == 0) {
            throw new BusinessException("CALLBACK_RECORD_NOT_FOUND", "Callback record not found");
        }
        return findRecord(id);
    }

    @Transactional
    public CallbackRecords.CallbackRecord markFailed(UUID id, String responseBody) {
        int updated = repository.markFailed(id, defaultValue(responseBody, "manual failed"),
                Instant.now(clock).plus(5, ChronoUnit.MINUTES));
        if (updated == 0) {
            throw new BusinessException("CALLBACK_RECORD_NOT_FOUND", "Callback record not found");
        }
        return findRecord(id);
    }

    @Transactional
    public CallbackRecords.CallbackRecord replay(UUID id) {
        int updated = repository.replay(id, Instant.now(clock));
        if (updated == 0) {
            throw new BusinessException("CALLBACK_RECORD_NOT_FOUND", "Callback record not found");
        }
        return findRecord(id);
    }

    private CallbackRecords.CallbackRecord createNewRecord(CallbackCreateCommand command) {
        OrderCallbackTarget order = repository.findOrderTarget(command.orderId())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found"));
        String requestBody = requestBody(command, order);
        repository.createRecord(
                UUID.randomUUID(),
                order.tenantId(),
                order.orderId(),
                command.callbackType(),
                command.businessId(),
                order.callbackUrl(),
                requestBody,
                "PENDING"
        );
        return repository.findByBusinessKey(command.callbackType(), command.businessId())
                .orElseThrow(() -> new BusinessException("CALLBACK_RECORD_CREATE_FAILED", "Callback record create failed"));
    }

    private void dispatchRecord(CallbackRecords.CallbackRecord record) {
        CallbackSendResult result;
        try {
            result = sender.send(record);
        } catch (RuntimeException ex) {
            result = CallbackSendResult.failure(ex.getMessage());
        }
        String responseBody = defaultValue(result.responseBody(), result.success() ? "callback success" : "callback failed");
        if (result.success()) {
            repository.markSucceeded(record.id(), responseBody);
            log.info("callback dispatched id={} type={} businessId={} status=SUCCESS",
                    record.id(), record.callbackType(), record.businessId());
            return;
        }
        int nextRetryCount = record.retryCount() + 1;
        if (nextRetryCount >= properties.getMaxRetries()) {
            repository.markDead(record.id(), responseBody);
            log.warn("callback dispatched id={} type={} businessId={} status=DEAD retryCount={} reason={}",
                    record.id(), record.callbackType(), record.businessId(), nextRetryCount, responseBody);
            return;
        }
        Instant nextRetryAt = nextRetryAt(nextRetryCount);
        repository.markFailed(record.id(), responseBody, nextRetryAt);
        log.warn("callback dispatched id={} type={} businessId={} status=FAILED retryCount={} nextRetryAt={} reason={}",
                record.id(), record.callbackType(), record.businessId(), nextRetryCount, nextRetryAt, responseBody);
    }

    private Instant nextRetryAt(int nextRetryCount) {
        long factor = 1L << Math.min(Math.max(nextRetryCount - 1, 0), 30);
        long seconds = Math.min(
                properties.getInitialRetryDelaySeconds() * factor,
                properties.getMaxRetryDelaySeconds()
        );
        return Instant.now(clock).plus(Duration.ofSeconds(seconds));
    }

    private CallbackRecords.CallbackRecord findRecord(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("CALLBACK_RECORD_NOT_FOUND", "Callback record not found"));
    }

    private String requestBody(CallbackCreateCommand command, OrderCallbackTarget order) {
        StringBuilder builder = new StringBuilder("{");
        appendJson(builder, "orderId", order.orderId().toString(), true);
        appendJson(builder, "orderNo", order.orderNo(), false);
        appendJson(builder, "callbackType", command.callbackType(), false);
        appendJson(builder, "businessId", command.businessId(), false);
        appendJson(builder, "businessStatus", defaultValue(command.businessStatus(), order.orderStatus()), false);
        appendJson(builder, "source", defaultValue(command.source(), "callback-service"), false);
        appendJson(builder, "createdAt", Instant.now(clock).toString(), false);
        builder.append('}');
        return builder.toString();
    }

    private void appendJson(StringBuilder builder, String key, String value, boolean first) {
        if (!first) {
            builder.append(',');
        }
        builder.append('"').append(escapeJson(key)).append('"').append(':')
                .append('"').append(escapeJson(value)).append('"');
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private void requireText(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(code, message);
        }
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
