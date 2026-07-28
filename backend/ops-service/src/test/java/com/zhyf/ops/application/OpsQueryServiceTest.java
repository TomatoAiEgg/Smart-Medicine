package com.zhyf.ops.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import com.zhyf.ops.infrastructure.OpsQueryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OpsQueryServiceTest {

    private final OpsQueryRepository repository = Mockito.mock(OpsQueryRepository.class);
    private final OpsQueryService service = new OpsQueryService(repository);

    @Test
    void shouldUseDefaultLimitWhenLimitIsInvalid() {
        service.listOutbox("NEW", "ORDER_CREATED", 0);

        verify(repository).findEventOutbox("NEW", "ORDER_CREATED", 50);
    }

    @Test
    void shouldCapLimitAtMaxLimit() {
        service.listMessageConsumeLogs("SUCCESS", "group-1", "event-1", 500);

        verify(repository).findMessageConsumeLogs("SUCCESS", "group-1", "event-1", 200);
    }

    @Test
    void shouldNormalizeLimitForLogisticsCallbackIssues() {
        service.listLogisticsCallbackIssues("FAILED", "ORDER_SHIPPED", "SF123", "ZHYF1", 0);

        verify(repository).findLogisticsCallbackIssues("FAILED", "ORDER_SHIPPED", "SF123", "ZHYF1", 50);
    }

    @Test
    void shouldNormalizeLimitForIntegrationRetryIssues() {
        service.listIntegrationRetryIssues("FAILED", "ADDRESS_PUSH", "ZHYF1", "HOSP-E2E", 500);

        verify(repository).findIntegrationRetryIssues("FAILED", "ADDRESS_PUSH", "ZHYF1", "HOSP-E2E", 200);
    }

    @Test
    void shouldNormalizeLimitForProblemRegistrations() {
        service.listProblemRegistrations(null, "ZHYF1", "破损", 0);

        verify(repository).findProblemRegistrations(null, "ZHYF1", "破损", 50);
    }

    @Test
    void shouldRequireCloseReasonForProblemRegistration() {
        UUID id = UUID.randomUUID();
        when(repository.findProblemRegistrationById(id)).thenReturn(List.of(problemRegistration(id)));

        assertThatThrownBy(() -> service.handleProblemRegistration(
                id,
                new OpsRecords.ProblemRegistrationHandleCommand("CLOSED", null, null, "ops", "")
        )).hasMessageContaining("Close reason is required");
    }

    @Test
    void shouldNormalizeLimitForDeadLetters() {
        service.listDeadLetters(null, "zhyf-order-event", "event-1", 500);

        verify(repository).findDeadLetters(null, "zhyf-order-event", "event-1", 200);
    }

    @Test
    void shouldReplayOpenDeadLetter() {
        UUID id = UUID.randomUUID();
        when(repository.findDeadLetterById(id)).thenReturn(List.of(deadLetter(id, "OPEN")));
        when(repository.resetDeadLetterForReplay(id)).thenReturn(1);
        when(repository.markDeadLetterReplayed(id, "ops", "retry")).thenReturn(1);

        service.replayDeadLetter(id, "ops", "retry");

        verify(repository).resetDeadLetterForReplay(id);
        verify(repository).markDeadLetterReplayed(id, "ops", "retry");
    }

    @Test
    void shouldUseDefaultRecentHoursWhenHealthWindowIsInvalid() {
        service.healthOverview(0);

        verify(repository).loadHealthOverview(24);
    }

    @Test
    void shouldCapRecentHoursForHealthOverview() {
        service.healthOverview(500);

        verify(repository).loadHealthOverview(168);
    }

    private OpsRecords.DeadLetterRecord deadLetter(UUID id, String status) {
        return new OpsRecords.DeadLetterRecord(
                id,
                "event-1",
                "zhyf-order-event",
                "ORDER_CREATED",
                null,
                "order-1",
                "publish failed",
                16,
                status,
                null,
                null,
                Instant.parse("2026-07-21T00:00:00Z"),
                Instant.parse("2026-07-21T00:00:00Z")
        );
    }

    private OpsRecords.ProblemRegistrationRecord problemRegistration(UUID id) {
        return new OpsRecords.ProblemRegistrationRecord(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ZHYF1",
                "HIS-1",
                "演示机构",
                "ORDER",
                "包裹破损",
                "联系补发",
                BigDecimal.ZERO,
                "OPEN",
                "ops",
                null,
                Instant.parse("2026-07-21T00:00:00Z"),
                Instant.parse("2026-07-21T00:00:00Z"),
                null,
                null
        );
    }
}
