package com.zhyf.message.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.message.infrastructure.OrderValidationRecordRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderCreatedEventHandlerTest {

    private final RecordingRepository repository = new RecordingRepository();
    private final OrderCreatedEventHandler handler = new OrderCreatedEventHandler(
            new ObjectMapper(),
            repository
    );

    @Test
    void shouldRecordPassedWhenOrderCreatedEventIsValid() {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = """
                {"tenantId":"%s","orderId":"%s","orderNo":"ZHYF1","externalOrderNo":"EXT1","prescriptionIds":["%s"]}
                """.formatted(tenantId, orderId, UUID.randomUUID());

        handler.handle(new MessageEvent("event-1", "ORDER_CREATED", "ORDER", orderId.toString(), "msg-1", payload));

        assertThat(repository.validationStatus).isEqualTo("PASSED");
        assertThat(repository.validationMessage).isEqualTo("基础校验通过");
        assertThat(repository.tenantId).isEqualTo(tenantId);
        assertThat(repository.orderId).isEqualTo(orderId);
    }

    @Test
    void shouldRecordRejectedWhenOrderCreatedEventMissesBusinessFields() {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = """
                {"tenantId":"%s","orderId":"%s","orderNo":"ZHYF1","prescriptionIds":[]}
                """.formatted(tenantId, orderId);

        handler.handle(new MessageEvent("event-2", "ORDER_CREATED", "ORDER", orderId.toString(), "msg-2", payload));

        assertThat(repository.validationStatus).isEqualTo("REJECTED");
        assertThat(repository.validationMessage).contains("externalOrderNo 缺失");
        assertThat(repository.validationMessage).contains("prescriptionIds 缺失");
    }

    @Test
    void shouldRejectWhenPayloadOrderIdDoesNotMatchAggregateId() {
        UUID tenantId = UUID.randomUUID();
        UUID aggregateOrderId = UUID.randomUUID();
        UUID payloadOrderId = UUID.randomUUID();
        String payload = """
                {"tenantId":"%s","orderId":"%s","orderNo":"ZHYF1","externalOrderNo":"EXT1","prescriptionIds":["%s"]}
                """.formatted(tenantId, payloadOrderId, UUID.randomUUID());

        handler.handle(new MessageEvent("event-3", "ORDER_CREATED", "ORDER", aggregateOrderId.toString(), "msg-3", payload));

        assertThat(repository.validationStatus).isEqualTo("REJECTED");
        assertThat(repository.validationMessage).contains("orderId 与 aggregateId 不一致");
    }

    private static class RecordingRepository implements OrderValidationRecordRepository {

        private UUID tenantId;
        private UUID orderId;
        private String validationStatus;
        private String validationMessage;

        @Override
        public void insert(
                UUID id,
                UUID tenantId,
                UUID orderId,
                String eventId,
                String validationStatus,
                String validationMessage,
                String rawPayload
        ) {
            this.tenantId = tenantId;
            this.orderId = orderId;
            this.validationStatus = validationStatus;
            this.validationMessage = validationMessage;
        }
    }

}
