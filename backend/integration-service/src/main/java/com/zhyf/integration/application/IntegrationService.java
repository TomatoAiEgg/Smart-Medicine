package com.zhyf.integration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.integration.infrastructure.IntegrationRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IntegrationService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final String COMMUNITY_SOURCE_TYPE = "COMMUNITY_HOSPITAL";
    private static final String ADDRESS_SOURCE_TYPE = "ADDRESS_PUSH";

    private final IntegrationRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IntegrationService(IntegrationRepository repository) {
        this.repository = repository;
    }

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

    public IntegrationRecords.IntegrationMessageRecord recordAddressPush(IntegrationCommands.AddressPushCommand command) {
        if (command.supplementId() == null) {
            throw new BusinessException("SUPPLEMENT_ID_REQUIRED", "Supplement id is required");
        }
        String hospitalCode = required(command.hospitalCode(), "HOSPITAL_CODE_REQUIRED", "Hospital code is required");
        String adapterCode = required(command.adapterCode(), "ADAPTER_CODE_REQUIRED", "Adapter code is required");
        String orderNo = required(command.orderNo(), "ORDER_NO_REQUIRED", "Order no is required");
        String externalMessageId = command.supplementId().toString();
        return repository.findMessage(ADDRESS_SOURCE_TYPE, hospitalCode, externalMessageId)
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
