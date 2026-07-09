package com.zhyf.integration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.integration.infrastructure.IntegrationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class IntegrationService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final String COMMUNITY_SOURCE_TYPE = "COMMUNITY_HOSPITAL";
    private static final String ADDRESS_SOURCE_TYPE = "ADDRESS_PUSH";
    private static final String COMMUNITY_STATUS_SOURCE_TYPE = "COMMUNITY_STATUS_PUSH";

    private final IntegrationRepository repository;
    private final IntegrationTaskSender sender;
    private final IntegrationProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public IntegrationService(
            IntegrationRepository repository,
            IntegrationTaskSender sender,
            IntegrationProperties properties
    ) {
        this(repository, sender, properties, Clock.systemUTC());
    }

    IntegrationService(
            IntegrationRepository repository,
            IntegrationTaskSender sender,
            IntegrationProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IntegrationRecords.IntegrationMessageRecord recordCommunityMessage(
            IntegrationCommands.CommunityMessageCommand command
    ) {
        String communityCode = required(command.communityCode(), "COMMUNITY_CODE_REQUIRED", "Community code is required");
        String externalMessageId = required(
                command.externalMessageId(),
                "EXTERNAL_MESSAGE_ID_REQUIRED",
                "External message id is required"
        );
        String messageType = required(command.messageType(), "MESSAGE_TYPE_REQUIRED", "Message type is required");
        String businessKey = normalize(command.businessKey());
        return repository.findMessage(COMMUNITY_SOURCE_TYPE, communityCode, externalMessageId)
                .orElseGet(() -> repository.createMessage(
                        UUID.randomUUID(),
                        COMMUNITY_SOURCE_TYPE,
                        communityCode,
                        externalMessageId,
                        messageType,
                        businessKey,
                        json(communityPayload(command.areaCode(), communityCode, businessKey)),
                        raw(command.rawPayload())
                ));
    }

    @Transactional
    public IntegrationRecords.IntegrationMessageRecord recordAddressPush(IntegrationCommands.AddressPushCommand command) {
        if (command.supplementId() == null) {
            throw new BusinessException("SUPPLEMENT_ID_REQUIRED", "Supplement id is required");
        }
        String hospitalCode = required(command.hospitalCode(), "HOSPITAL_CODE_REQUIRED", "Hospital code is required");
        String adapterCode = required(command.adapterCode(), "ADAPTER_CODE_REQUIRED", "Adapter code is required");
        String orderNo = required(command.orderNo(), "ORDER_NO_REQUIRED", "Order no is required");
        String externalMessageId = command.supplementId().toString();
        IntegrationRecords.IntegrationMessageRecord message = repository.findMessage(ADDRESS_SOURCE_TYPE, hospitalCode, externalMessageId)
                .orElseGet(() -> repository.createMessage(
                        UUID.randomUUID(),
                        ADDRESS_SOURCE_TYPE,
                        hospitalCode,
                        externalMessageId,
                        adapterCode,
                        orderNo,
                        json(Map.of(
                                "supplementId", externalMessageId,
                                "hospitalCode", hospitalCode,
                                "adapterCode", adapterCode,
                                "orderNo", orderNo
                        )),
                        raw(command.rawPayload())
                ));
        createRetryTaskIfNeeded(
                message,
                ADDRESS_SOURCE_TYPE,
                hospitalCode,
                orderNo,
                normalize(command.requestUrl()),
                raw(command.rawPayload())
        );
        return message;
    }

    @Transactional
    public IntegrationRecords.IntegrationMessageRecord createCommunityStatusPush(
            IntegrationCommands.CommunityStatusPushCommand command
    ) {
        String communityCode = required(command.communityCode(), "COMMUNITY_CODE_REQUIRED", "Community code is required");
        String orderNo = required(command.orderNo(), "ORDER_NO_REQUIRED", "Order no is required");
        String status = required(command.status(), "STATUS_REQUIRED", "Status is required");
        String requestUrl = required(command.requestUrl(), "REQUEST_URL_REQUIRED", "Request url is required");
        String externalMessageId = orderNo + ":" + status;
        IntegrationRecords.IntegrationMessageRecord message = repository.findMessage(
                COMMUNITY_STATUS_SOURCE_TYPE,
                communityCode,
                externalMessageId
        ).orElseGet(() -> repository.createMessage(
                UUID.randomUUID(),
                COMMUNITY_STATUS_SOURCE_TYPE,
                communityCode,
                externalMessageId,
                status,
                orderNo,
                json(Map.of("communityCode", communityCode, "orderNo", orderNo, "status", status)),
                raw(command.rawPayload())
        ));
        createRetryTaskIfNeeded(
                message,
                COMMUNITY_STATUS_SOURCE_TYPE,
                communityCode,
                orderNo,
                requestUrl,
                raw(command.rawPayload())
        );
        return message;
    }

    public IntegrationRecords.HospitalOrderRecord findHospitalOrderByPrescription(String prescriptionNo, String phone) {
        String normalizedPrescriptionNo = required(
                prescriptionNo,
                "PRESCRIPTION_NO_REQUIRED",
                "Prescription no is required"
        );
        String normalizedPhone = required(phone, "PHONE_REQUIRED", "Phone is required");
        return repository.findHospitalOrderByPrescription(normalizedPrescriptionNo, normalizedPhone)
                .orElseThrow(() -> new BusinessException("HOSPITAL_ORDER_NOT_FOUND", "Hospital order not found"));
    }

    public List<IntegrationRecords.IntegrationMessageRecord> listMessages(
            String sourceType,
            String processStatus,
            String businessKey,
            int limit
    ) {
        return repository.findMessages(
                normalize(sourceType),
                normalize(processStatus),
                normalize(businessKey),
                normalizeLimit(limit)
        );
    }

    public List<IntegrationRecords.IntegrationRetryTaskRecord> listRetryTasks(
            String taskType,
            String taskStatus,
            String businessKey,
            int limit
    ) {
        return repository.findRetryTasks(
                normalize(taskType),
                normalize(taskStatus),
                normalize(businessKey),
                normalizeLimit(limit)
        );
    }

    public int dispatchDueRetryTasks(int limit) {
        List<IntegrationRecords.IntegrationRetryTaskRecord> tasks = repository.findDueRetryTasks(
                Instant.now(clock),
                normalizeLimit(limit)
        );
        for (IntegrationRecords.IntegrationRetryTaskRecord task : tasks) {
            dispatchRetryTask(task);
        }
        return tasks.size();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String required(String value, String code, String message) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(code, message);
        }
        return normalized;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String raw(String value) {
        return value == null ? "" : value;
    }

    private void createRetryTaskIfNeeded(
            IntegrationRecords.IntegrationMessageRecord message,
            String taskType,
            String targetSystem,
            String businessKey,
            String requestUrl,
            String requestBody
    ) {
        if (!StringUtils.hasText(requestUrl)) {
            return;
        }
        repository.findRetryTask(message.messageId(), taskType)
                .orElseGet(() -> repository.createRetryTask(
                        UUID.randomUUID(),
                        message.messageId(),
                        taskType,
                        targetSystem,
                        businessKey,
                        requestUrl,
                        requestBody,
                        Instant.now(clock)
                ));
    }

    private void dispatchRetryTask(IntegrationRecords.IntegrationRetryTaskRecord task) {
        IntegrationSendResult result;
        try {
            result = sender.send(task);
        } catch (RuntimeException ex) {
            result = IntegrationSendResult.failure(ex.getMessage());
        }
        String responseBody = defaultValue(result.responseBody(), result.success() ? "integration send success" : "integration send failed");
        if (result.success()) {
            repository.markRetryTaskSucceeded(task.taskId(), responseBody);
            repository.markMessageStatus(task.messageId(), "SUCCESS", null);
            log.info("integration retry dispatched taskId={} type={} businessKey={} status=SUCCESS",
                    task.taskId(), task.taskType(), task.businessKey());
            return;
        }
        int nextRetryCount = task.retryCount() + 1;
        if (nextRetryCount >= properties.getMaxRetries()) {
            repository.markRetryTaskDead(task.taskId(), responseBody);
            repository.markMessageStatus(task.messageId(), "DEAD", responseBody);
            log.warn("integration retry dispatched taskId={} type={} businessKey={} status=DEAD retryCount={} reason={}",
                    task.taskId(), task.taskType(), task.businessKey(), nextRetryCount, responseBody);
            return;
        }
        Instant nextRetryAt = nextRetryAt(nextRetryCount);
        repository.markRetryTaskFailed(task.taskId(), responseBody, nextRetryAt);
        repository.markMessageStatus(task.messageId(), "FAILED", responseBody);
        log.warn("integration retry dispatched taskId={} type={} businessKey={} status=FAILED retryCount={} nextRetryAt={} reason={}",
                task.taskId(), task.taskType(), task.businessKey(), nextRetryCount, nextRetryAt, responseBody);
    }

    private Instant nextRetryAt(int nextRetryCount) {
        long factor = 1L << Math.min(Math.max(nextRetryCount - 1, 0), 30);
        long seconds = Math.min(properties.getInitialRetryDelaySeconds() * factor, properties.getMaxRetryDelaySeconds());
        return Instant.now(clock).plus(Duration.ofSeconds(seconds));
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private Map<String, String> communityPayload(String areaCode, String communityCode, String businessKey) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("areaCode", normalize(areaCode));
        payload.put("communityCode", communityCode);
        payload.put("businessKey", businessKey);
        return payload;
    }

    private String json(Map<String, String> values) {
        try {
            Map<String, String> normalizedValues = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                if (value != null) {
                    normalizedValues.put(key, value);
                }
            });
            return objectMapper.writeValueAsString(normalizedValues);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("INTEGRATION_JSON_WRITE_FAILED", "JSON serialize failed");
        }
    }
}
