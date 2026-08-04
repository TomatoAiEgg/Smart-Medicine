package com.zhyf.callback.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(repository.claimDueRecords(anyString(), eq(Instant.now(clock)),
                eq(Instant.now(clock).plusSeconds(120)), eq(20))).thenReturn(List.of(record));
        when(sender.send(record)).thenReturn(CallbackSendResult.success("ok"));
        when(repository.completeClaimSucceeded(eq(record.id()), anyString(), eq("ok"))).thenReturn(1);

        int handled = service.dispatchDueCallbacks(20);

        assertThat(handled).isEqualTo(1);
        verify(repository).completeClaimSucceeded(eq(record.id()), anyString(), eq("ok"));
    }

    @Test
    void shouldScheduleRetryWhenSenderReturnsFailureBeforeMaxRetries() {
        properties.setMaxRetries(3);
        properties.setInitialRetryDelaySeconds(60);
        CallbackRecords.CallbackRecord record = record(UUID.randomUUID(), UUID.randomUUID());
        when(repository.claimDueRecords(anyString(), eq(Instant.now(clock)),
                eq(Instant.now(clock).plusSeconds(120)), eq(20))).thenReturn(List.of(record));
        when(sender.send(record)).thenReturn(CallbackSendResult.failure("http 500"));
        when(repository.completeClaimFailed(eq(record.id()), anyString(), eq("http 500"),
                eq(Instant.now(clock).plus(60, ChronoUnit.SECONDS)))).thenReturn(1);

        int handled = service.dispatchDueCallbacks(20);

        assertThat(handled).isEqualTo(1);
        verify(repository).completeClaimFailed(eq(record.id()), anyString(), eq("http 500"),
                eq(Instant.now(clock).plus(60, ChronoUnit.SECONDS)));
    }

    @Test
    void shouldMarkDeadWhenSenderFailsAtMaxRetries() {
        properties.setMaxRetries(3);
        CallbackRecords.CallbackRecord record = record(UUID.randomUUID(), UUID.randomUUID(), 2);
        when(repository.claimDueRecords(anyString(), eq(Instant.now(clock)),
                eq(Instant.now(clock).plusSeconds(120)), eq(20))).thenReturn(List.of(record));
        when(sender.send(record)).thenReturn(CallbackSendResult.failure("timeout"));
        when(repository.completeClaimDead(eq(record.id()), anyString(), eq("timeout"))).thenReturn(1);

        int handled = service.dispatchDueCallbacks(20);

        assertThat(handled).isEqualTo(1);
        verify(repository).completeClaimDead(eq(record.id()), anyString(), eq("timeout"));
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
