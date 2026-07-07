package com.zhyf.callback.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.callback.infrastructure.CallbackRepository;
import com.zhyf.callback.infrastructure.CallbackRepository.OrderCallbackTarget;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CallbackServiceTest {

    private final CallbackRepository repository = Mockito.mock(CallbackRepository.class);
    private final CallbackSender sender = Mockito.mock(CallbackSender.class);
    private final CallbackProperties properties = new CallbackProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-07T00:00:00Z"), ZoneOffset.UTC);
    private final CallbackService service = new CallbackService(repository, sender, properties, clock);

    @Test
    void shouldCreatePendingRecordFromOrderTarget() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CallbackCreateCommand command = new CallbackCreateCommand(
                orderId,
                "ORDER_SHIPPED",
                "SHIP-1",
                "SHIPPED",
                "logistics-service"
        );
        CallbackRecords.CallbackRecord record = record(orderId, tenantId);
        when(repository.findByBusinessKey("ORDER_SHIPPED", "SHIP-1")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(record));
        when(repository.findOrderTarget(orderId)).thenReturn(Optional.of(new OrderCallbackTarget(
                orderId,
                tenantId,
                "ZHYF1",
                "http://example.invalid/callback",
                "SHIPPED"
        )));

        CallbackRecords.CallbackRecord result = service.createCallback(command);

        assertThat(result.status()).isEqualTo("PENDING");
        verify(repository).createRecord(
                any(),
                eq(tenantId),
                eq(orderId),
                eq("ORDER_SHIPPED"),
                eq("SHIP-1"),
                eq("http://example.invalid/callback"),
                eq("{\"orderId\":\"" + orderId + "\",\"orderNo\":\"ZHYF1\","
                        + "\"callbackType\":\"ORDER_SHIPPED\",\"businessId\":\"SHIP-1\","
                        + "\"businessStatus\":\"SHIPPED\",\"source\":\"logistics-service\","
                        + "\"createdAt\":\"2026-07-07T00:00:00Z\"}"),
                eq("PENDING")
        );
    }

    @Test
    void shouldMarkCallbackSucceededWhenSenderReturnsSuccess() {
        CallbackRecords.CallbackRecord record = record(UUID.randomUUID(), UUID.randomUUID());
        when(repository.findDueRecords(Instant.now(clock), 20)).thenReturn(List.of(record));
        when(sender.send(record)).thenReturn(CallbackSendResult.success("ok"));

        int handled = service.dispatchDueCallbacks(20);

        assertThat(handled).isEqualTo(1);
        verify(repository).markSucceeded(record.id(), "ok");
    }

    @Test
    void shouldScheduleRetryWhenSenderReturnsFailureBeforeMaxRetries() {
        properties.setMaxRetries(3);
        properties.setInitialRetryDelaySeconds(60);
        CallbackRecords.CallbackRecord record = record(UUID.randomUUID(), UUID.randomUUID());
        when(repository.findDueRecords(Instant.now(clock), 20)).thenReturn(List.of(record));
        when(sender.send(record)).thenReturn(CallbackSendResult.failure("http 500"));

        int handled = service.dispatchDueCallbacks(20);

        assertThat(handled).isEqualTo(1);
        verify(repository).markFailed(record.id(), "http 500", Instant.now(clock).plus(60, ChronoUnit.SECONDS));
    }

    @Test
    void shouldMarkDeadWhenSenderFailsAtMaxRetries() {
        properties.setMaxRetries(3);
        CallbackRecords.CallbackRecord record = record(UUID.randomUUID(), UUID.randomUUID(), 2);
        when(repository.findDueRecords(Instant.now(clock), 20)).thenReturn(List.of(record));
        when(sender.send(record)).thenReturn(CallbackSendResult.failure("timeout"));

        int handled = service.dispatchDueCallbacks(20);

        assertThat(handled).isEqualTo(1);
        verify(repository).markDead(record.id(), "timeout");
    }

    private CallbackRecords.CallbackRecord record(UUID orderId, UUID tenantId) {
        return record(orderId, tenantId, 0);
    }

    private CallbackRecords.CallbackRecord record(UUID orderId, UUID tenantId, int retryCount) {
        return new CallbackRecords.CallbackRecord(
                UUID.randomUUID(),
                tenantId,
                orderId,
                "ZHYF1",
                "ORDER_SHIPPED",
                "SHIP-1",
                "http://example.invalid/callback",
                "{}",
                null,
                "PENDING",
                retryCount,
                null,
                Instant.now(clock),
                Instant.now(clock)
        );
    }
}
