package com.zhyf.workflow.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.workflow.infrastructure.OrderValidationRecordRepository;
import com.zhyf.workflow.infrastructure.WorkflowTaskRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderCreatedWorkflowServiceTest {

    private final OrderValidationRecordRepository validationRecordRepository =
            Mockito.mock(OrderValidationRecordRepository.class);
    private final WorkflowTaskRepository taskRepository = Mockito.mock(WorkflowTaskRepository.class);
    private final OrderCreatedWorkflowService service = new OrderCreatedWorkflowService(
            new ObjectMapper(),
            validationRecordRepository,
            taskRepository,
            3,
            0
    );

    @Test
    void shouldCreateReviewTaskWhenValidationPassed() {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String eventId = "event-1";
        String payload = """
                {"tenantId":"%s","orderId":"%s","orderNo":"ZHYF1","externalOrderNo":"EXT1","prescriptionIds":["rx1"]}
                """.formatted(tenantId, orderId);
        when(validationRecordRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(
                new OrderValidationRecordRepository.OrderValidationRecord(
                        tenantId,
                        orderId,
                        eventId,
                        "PASSED",
                        "基础校验通过",
                        payload
                )
        ));

        service.createReviewTaskIfValidationPassed(eventId, orderId.toString(), payload);

        verify(taskRepository).createOrderReviewTask(any(UUID.class), eq(tenantId), eq(orderId), eq(eventId), eq(payload));
    }

    @Test
    void shouldWaitWhenValidationRecordIsNotReadyYet() {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String eventId = "event-wait-1";
        String payload = """
                {"tenantId":"%s","orderId":"%s","orderNo":"ZHYF1","externalOrderNo":"EXT1","prescriptionIds":["rx1"]}
                """.formatted(tenantId, orderId);
        when(validationRecordRepository.findLatestByOrderId(orderId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new OrderValidationRecordRepository.OrderValidationRecord(
                        tenantId,
                        orderId,
                        eventId,
                        "PASSED",
                        "基础校验通过",
                        payload
                )));

        service.createReviewTaskIfValidationPassed(eventId, orderId.toString(), payload);

        verify(taskRepository).createOrderReviewTask(any(UUID.class), eq(tenantId), eq(orderId), eq(eventId), eq(payload));
    }

    @Test
    void shouldSkipReviewTaskWhenValidationRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String eventId = "event-2";
        String payload = """
                {"tenantId":"%s","orderId":"%s","orderNo":"ZHYF1","externalOrderNo":"EXT1","prescriptionIds":["rx1"]}
                """.formatted(tenantId, orderId);
        when(validationRecordRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(
                new OrderValidationRecordRepository.OrderValidationRecord(
                        tenantId,
                        orderId,
                        eventId,
                        "REJECTED",
                        "字段缺失",
                        payload
                )
        ));

        service.createReviewTaskIfValidationPassed(eventId, orderId.toString(), payload);

        verifyNoInteractions(taskRepository);
    }
}
