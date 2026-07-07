package com.zhyf.decoction.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.decoction.domain.DecoctionTaskSnapshot;
import com.zhyf.decoction.domain.DecoctionTaskStatus;
import com.zhyf.decoction.infrastructure.DecoctionTaskRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DecoctionRejectedEventRecorderTest {

    private final DecoctionTaskRepository repository = Mockito.mock(DecoctionTaskRepository.class);
    private final DecoctionRejectedEventRecorder recorder = new DecoctionRejectedEventRecorder(repository);

    @Test
    void shouldCreateRejectedEventWhenOperationWasNotRecorded() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DecoctionTaskSnapshot task = task(taskId, tenantId, orderId);
        Instant eventTime = Instant.parse("2026-06-27T00:00:00Z");
        when(repository.findEventByOperationId("REJECTED-1")).thenReturn(Optional.empty());

        recorder.recordRejected(
                task,
                "REJECTED-1",
                "operator-1",
                "FINISH",
                "mes-decoction-finish",
                "CANCELLED",
                "DECOCTING",
                "{\"reason\":\"invalid\"}",
                eventTime
        );

        verify(repository).createTaskEvent(
                any(),
                eq(taskId),
                eq(tenantId),
                eq(orderId),
                eq("REJECTED"),
                eq("REJECTED-1"),
                eq("operator-1"),
                eq("{\"reason\":\"invalid\"}"),
                eq(eventTime)
        );
        verify(repository).createDeviceWorkRecord(
                any(),
                eq(task),
                eq("FINISH"),
                eq("REJECTED"),
                eq("CANCELLED"),
                eq("CANCELLED"),
                eq("WORK-REJECTED-" + workRecordOperationId(taskId, "FINISH", "REJECTED-1")),
                eq("mes-decoction-finish"),
                eq("operator-1"),
                eq("{\"reason\":\"invalid\"}"),
                eq(eventTime)
        );
    }

    @Test
    void shouldSkipWhenRejectedOperationWasAlreadyRecorded() {
        UUID taskId = UUID.randomUUID();
        DecoctionTaskSnapshot task = task(taskId, UUID.randomUUID(), UUID.randomUUID());
        when(repository.findEventByOperationId("REJECTED-1"))
                .thenReturn(Optional.of(event(taskId, "REJECTED-1")));

        recorder.recordRejected(
                task,
                "REJECTED-1",
                "operator-1",
                "FINISH",
                "mes-decoction-finish",
                "CANCELLED",
                "DECOCTING",
                "{\"reason\":\"invalid\"}",
                Instant.now()
        );

        verify(repository, never()).createTaskEvent(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(repository, never()).createDeviceWorkRecord(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private DecoctionTaskSnapshot task(UUID taskId, UUID tenantId, UUID orderId) {
        return new DecoctionTaskSnapshot(
                taskId,
                "DCT-1",
                tenantId,
                orderId,
                UUID.randomUUID(),
                "ZHYF1",
                "RX1",
                "DECOCT-001",
                "PAIL-1",
                DecoctionTaskStatus.DECOCTING.name(),
                "operator-1",
                null,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    private DecoctionTaskRepository.DecoctionTaskEventSnapshot event(UUID taskId, String operationId) {
        return new DecoctionTaskRepository.DecoctionTaskEventSnapshot(
                UUID.randomUUID(),
                taskId,
                "DCT-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "REJECTED",
                operationId,
                "operator-1",
                "{}",
                Instant.now(),
                Instant.now()
        );
    }

    private String workRecordOperationId(UUID taskId, String actionType, String eventOperationId) {
        String raw = taskId + ":WORK:" + actionType + ":" + eventOperationId;
        return UUID.nameUUIDFromBytes(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
