package com.zhyf.order.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.security.SignatureUtils;
import com.zhyf.common.status.OrderStatus;
import com.zhyf.common.status.PrescriptionStatus;
import com.zhyf.order.domain.InstitutionApp;
import com.zhyf.order.domain.OrderProgressSnapshot;
import com.zhyf.order.infrastructure.OrderRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderCreateResult createOrder(OrderCreateCommand command) {
        String rawPayload = writeJson(command.payload());
        InstitutionApp app = orderRepository.findEnabledApp(command.appKey())
                .orElseThrow(() -> new BusinessException("APP_NOT_FOUND", "机构应用不存在或已停用"));

        verifySignature(command, app, rawPayload);

        String externalOrderNo = readText(command.payload(), "externalOrderNo", "orderNo", "prescriptionOrderNo");
        if (!StringUtils.hasText(externalOrderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "外部订单号不能为空");
        }

        return orderRepository.findOrderByExternalNo(app.tenantId(), app.institutionId(), externalOrderNo)
                .map(existing -> new OrderCreateResult(
                        existing.orderId(),
                        existing.orderNo(),
                        externalOrderNo,
                        existing.status(),
                        true
                ))
                .orElseGet(() -> createNewOrder(app, externalOrderNo, command.payload(), rawPayload));
    }

    public OrderCreateResult getOrder(String orderNo) {
        return orderRepository.findOrderByOrderNo(orderNo)
                .map(existing -> new OrderCreateResult(
                        existing.orderId(),
                        existing.orderNo(),
                        existing.externalOrderNo(),
                        existing.status(),
                        false
                ))
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
    }

    public OrderProgressSnapshot getOrderProgress(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        return orderRepository.findOrderProgressByOrderNo(orderNo.trim())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
    }

    private OrderCreateResult createNewOrder(
            InstitutionApp app,
            String externalOrderNo,
            JsonNode payload,
            String rawPayload
    ) {
        UUID orderId = UUID.randomUUID();
        String orderNo = "ZHYF" + Instant.now().toEpochMilli();
        String patientName = readText(payload, "patientName", "patient_name", "patient");
        String patientPhone = readText(payload, "patientPhone", "patient_phone", "patientTel");
        String receiverName = readText(payload, "receiverName", "consignee", "receiver");
        String receiverPhone = readText(payload, "receiverPhone", "conTel", "receiverTel");
        String receiverAddress = readText(payload, "receiverAddress", "address", "addrDetail");

        orderRepository.insertOrder(
                orderId,
                app.tenantId(),
                app.institutionId(),
                orderNo,
                externalOrderNo,
                OrderStatus.CREATED.name(),
                patientName,
                patientPhone,
                receiverName,
                receiverPhone,
                receiverAddress,
                app.callbackUrl(),
                rawPayload
        );
        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                app.tenantId(),
                orderId,
                null,
                OrderStatus.CREATED.name(),
                "INSTITUTION",
                "createOrder"
        );

        List<UUID> prescriptionIds = createPrescriptions(app, orderId, payload);
        String eventPayload = """
                {"tenantId":"%s","orderId":"%s","orderNo":"%s","externalOrderNo":"%s","prescriptionIds":%s}
                """.formatted(app.tenantId(), orderId, orderNo, externalOrderNo, writeJson(prescriptionIds));
        orderRepository.insertOutbox(
                UUID.randomUUID(),
                app.tenantId(),
                UUID.randomUUID().toString(),
                "ORDER_CREATED",
                "ORDER",
                orderId.toString(),
                eventPayload
        );

        return new OrderCreateResult(orderId, orderNo, externalOrderNo, OrderStatus.CREATED.name(), false);
    }

    private List<UUID> createPrescriptions(InstitutionApp app, UUID orderId, JsonNode payload) {
        JsonNode prescriptions = payload.get("prescriptions");
        List<UUID> prescriptionIds = new ArrayList<>();
        if (prescriptions == null || !prescriptions.isArray() || prescriptions.isEmpty()) {
            UUID prescriptionId = insertPrescription(app, orderId, payload, 1);
            prescriptionIds.add(prescriptionId);
            return prescriptionIds;
        }

        int sort = 1;
        for (JsonNode prescription : prescriptions) {
            UUID prescriptionId = insertPrescription(app, orderId, prescription, sort++);
            prescriptionIds.add(prescriptionId);
        }
        return prescriptionIds;
    }

    private UUID insertPrescription(InstitutionApp app, UUID orderId, JsonNode node, int sort) {
        UUID prescriptionId = UUID.randomUUID();
        String externalPrescriptionNo = readText(node, "externalPrescriptionNo", "prescriptionNo", "presNum");
        if (!StringUtils.hasText(externalPrescriptionNo)) {
            externalPrescriptionNo = "PRES-" + orderId + "-" + sort;
        }
        String prescriptionNo = "RX" + Instant.now().toEpochMilli() + sort;
        orderRepository.insertPrescription(
                prescriptionId,
                app.tenantId(),
                app.institutionId(),
                orderId,
                prescriptionNo,
                externalPrescriptionNo,
                PrescriptionStatus.CREATED.name(),
                readText(node, "doctorName", "doctor"),
                readText(node, "diagnosis"),
                writeJson(node)
        );

        JsonNode details = node.get("details");
        if (details == null) {
            details = node.get("drugs");
        }
        if (details != null && details.isArray()) {
            int detailSort = 1;
            for (JsonNode detail : details) {
                orderRepository.insertPrescriptionDetail(
                        UUID.randomUUID(),
                        app.tenantId(),
                        prescriptionId,
                        readText(detail, "drugCode", "medicineCode"),
                        readText(detail, "drugName", "medicineName"),
                        readText(detail, "dose", "dosage"),
                        readText(detail, "unit"),
                        detailSort++
                );
            }
        }
        return prescriptionId;
    }

    private void verifySignature(OrderCreateCommand command, InstitutionApp app, String rawPayload) {
        String bodyHash = SignatureUtils.sha256Hex(rawPayload);
        String source = command.appKey() + "\n" + command.timestamp() + "\n" + bodyHash;
        String expected = SignatureUtils.hmacSha256Hex(app.appSecret(), source);
        if (!SignatureUtils.constantTimeEquals(expected, command.signature())) {
            throw new BusinessException("INVALID_SIGNATURE", "签名错误");
        }
    }

    private String readText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON_WRITE_FAILED", "JSON 序列化失败");
        }
    }
}
