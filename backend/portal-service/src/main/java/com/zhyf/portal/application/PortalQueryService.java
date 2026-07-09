package com.zhyf.portal.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.portal.infrastructure.PortalRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PortalQueryService {

    private final PortalRepository repository;
    private final ObjectMapper objectMapper;

    public PortalQueryService(PortalRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public PortalRecords.PortalOrderRecord queryOrder(PortalCommands.PortalOrderQuery query) {
        String orderNo = normalize(query.orderNo());
        String externalOrderNo = normalize(query.externalOrderNo());
        String phone = normalize(query.phone());
        validateQuery(orderNo, externalOrderNo, phone);
        return repository.findOrderForPortal(orderNo, externalOrderNo, phone)
                .orElseThrow(() -> new BusinessException("PORTAL_ORDER_NOT_FOUND", "Order not found"));
    }

    public PortalRecords.AddressSupplementRecord createAddressSupplement(
            String orderNo,
            PortalCommands.AddressSupplementCommand command
    ) {
        String normalizedOrderNo = normalize(orderNo);
        String phone = normalize(command.phone());
        if (!StringUtils.hasText(normalizedOrderNo)) {
            throw new BusinessException("PORTAL_ORDER_NO_REQUIRED", "Order no is required");
        }
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException("PORTAL_PHONE_REQUIRED", "Query phone is required");
        }
        if (!StringUtils.hasText(command.receiverName())
                || !StringUtils.hasText(command.receiverPhone())
                || !StringUtils.hasText(command.receiverAddress())) {
            throw new BusinessException("PORTAL_ADDRESS_REQUIRED", "Receiver name, phone and address are required");
        }
        PortalRecords.PortalOrderRecord order = repository.findOrderForPortal(normalizedOrderNo, null, phone)
                .orElseThrow(() -> new BusinessException("PORTAL_ORDER_NOT_FOUND", "Order not found"));
        return repository.createAddressSupplement(UUID.randomUUID(), order, command, json(command));
    }

    private void validateQuery(String orderNo, String externalOrderNo, String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException("PORTAL_PHONE_REQUIRED", "Query phone is required");
        }
        if (!StringUtils.hasText(orderNo) && !StringUtils.hasText(externalOrderNo)) {
            throw new BusinessException("PORTAL_ORDER_KEY_REQUIRED", "Order no or external order no is required");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("PORTAL_JSON_WRITE_FAILED", "JSON serialize failed");
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
