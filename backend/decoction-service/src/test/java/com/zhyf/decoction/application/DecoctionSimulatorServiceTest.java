package com.zhyf.decoction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.status.OrderStatus;
import com.zhyf.decoction.config.DecoctionProperties;
import com.zhyf.decoction.domain.DecoctionTaskSnapshot;
import com.zhyf.decoction.domain.DecoctionTaskStatus;
import com.zhyf.decoction.domain.PrescriptionForDecoction;
import com.zhyf.decoction.infrastructure.DecoctionTaskRepository;
import com.zhyf.decoction.infrastructure.OrderStatusClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DecoctionSimulatorServiceTest {

    private final DecoctionTaskRepository repository = Mockito.mock(DecoctionTaskRepository.class);
    private final OrderStatusClient orderStatusClient = Mockito.mock(OrderStatusClient.class);
    private final DecoctionRejectedEventRecorder rejectedEventRecorder = Mockito.mock(DecoctionRejectedEventRecorder.class);
    private final DecoctionProperties properties = new DecoctionProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T00:00:00Z"), ZoneOffset.UTC);
    private final DecoctionSimulatorService service = new DecoctionSimulatorService(
            repository,
            orderStatusClient,
            rejectedEventRecorder,
            properties,
            clock
    );

    private UUID tenantId;
    private UUID orderId;
    private UUID prescriptionId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        prescriptionId = UUID.randomUUID();
    }

    @Test
    void shouldBindRecheckedPrescription() {
        SimulatorOperationCommand command = command("op-bind-1", "DECOCT-001", "RX1");
        PrescriptionForDecoction prescription = prescription(OrderStatus.RECHECKED.name());
        when(repository.findByBindOperationId("op-bind-1")).thenReturn(Optional.empty());
        when(repository.findPrescription("RX1")).thenReturn(Optional.of(prescription));
        when(repository.findActiveTaskByPrescriptionId(prescriptionId)).thenReturn(Optional.empty());
        when(repository.findActiveTaskByDeviceCode("DECOCT-001")).thenReturn(Optional.empty());
        when(repository.findByTaskId(any())).thenAnswer(invocation -> Optional.of(task(
                invocation.getArgument(0),
                "DCT-TEST",
                DecoctionTaskStatus.BOUND.name()
        )));

        DecoctionRecords.DecoctionTaskRecord result = service.bindPrescription(command);

        assertThat(result.taskStatus()).isEqualTo("BOUND");
        assertThat(result.prescriptionNo()).isEqualTo("RX1");
        ArgumentCaptor<UUID> taskIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(repository).createTask(
                taskIdCaptor.capture(),
                any(),
                eq(tenantId),
                eq(orderId),
                eq(prescriptionId),
                eq("RX1"),
                eq("DECOCT-001"),
                eq("PAIL-1"),
                eq("BOUND"),
                eq("op-bind-1"),
                eq("operator-1")
        );
        assertThat(taskIdCaptor.getValue()).isNotNull();
    }

    @Test
    void shouldReturnExistingTaskForDuplicateBindOperation() {
        DecoctionTaskSnapshot existing = task(UUID.randomUUID(), "DCT-1", DecoctionTaskStatus.BOUND.name());
        when(repository.findByBindOperationId("op-bind-1")).thenReturn(Optional.of(existing));

        DecoctionRecords.DecoctionTaskRecord result = service.bindPrescription(command("op-bind-1", "DECOCT-001", "RX1"));

        assertThat(result.taskNo()).isEqualTo("DCT-1");
        verify(repository, never()).createTask(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectOccupiedDeviceWhenBinding() {
        SimulatorOperationCommand command = command("op-bind-1", "DECOCT-001", "RX1");
        when(repository.findByBindOperationId("op-bind-1")).thenReturn(Optional.empty());
        when(repository.findPrescription("RX1")).thenReturn(Optional.of(prescription(OrderStatus.RECHECKED.name())));
        when(repository.findActiveTaskByPrescriptionId(prescriptionId)).thenReturn(Optional.empty());
        when(repository.findActiveTaskByDeviceCode("DECOCT-001"))
                .thenReturn(Optional.of(task(UUID.randomUUID(), "DCT-OLD", DecoctionTaskStatus.BOUND.name())));

        assertThatThrownBy(() -> service.bindPrescription(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Device is occupied by another task");
    }

    @Test
    void shouldStartBoundTaskAndAdvanceOrderStatus() {
        UUID taskId = UUID.randomUUID();
        when(repository.findByStartOperationId("op-start-1")).thenReturn(Optional.empty());
        when(repository.findActiveTaskByPrescriptionNo("RX1"))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.BOUND.name())));
        when(repository.markStarted(taskId, "op-start-1", "operator-1")).thenReturn(1);
        when(repository.findByTaskId(taskId))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.DECOCTING.name())));

        DecoctionRecords.DecoctionTaskRecord result = service.startDecoction(
                command("op-start-1", "DECOCT-001", "RX1")
        );

        assertThat(result.taskStatus()).isEqualTo("DECOCTING");
        verify(orderStatusClient).updateStatus(orderId, "DECOCTING", "DECOCTION", "pda-decoction-start");
    }

    @Test
    void shouldFinishDecoctingTaskAndAdvanceOrderStatus() {
        UUID taskId = UUID.randomUUID();
        when(repository.findByFinishOperationId("op-finish-1")).thenReturn(Optional.empty());
        when(repository.findActiveTaskByPrescriptionNo("RX1"))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.DECOCTING.name())));
        when(repository.markFinished(taskId, "op-finish-1", "operator-1")).thenReturn(1);
        when(repository.findByTaskId(taskId))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.DECOCTED.name())));

        DecoctionRecords.DecoctionTaskRecord result = service.finishDecoction(
                command("op-finish-1", "DECOCT-001", "RX1")
        );

        assertThat(result.taskStatus()).isEqualTo("DECOCTED");
        verify(orderStatusClient).updateStatus(orderId, "DECOCTED", "DECOCTION", "pda-decoction-finish");
    }

    @Test
    void shouldCancelBoundTaskAndKeepOrderStatus() {
        UUID taskId = UUID.randomUUID();
        when(repository.findByCancelOperationId("op-cancel-1")).thenReturn(Optional.empty());
        when(repository.findActiveTaskByPrescriptionNo("RX1"))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.BOUND.name())));
        when(repository.markCancelled(taskId, "op-cancel-1", "operator-1")).thenReturn(1);
        when(repository.findByTaskId(taskId))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.CANCELLED.name())));

        DecoctionRecords.DecoctionTaskRecord result = service.cancelDecoction(
                command("op-cancel-1", "DECOCT-001", "RX1")
        );

        assertThat(result.taskStatus()).isEqualTo("CANCELLED");
        verify(orderStatusClient, never()).updateStatus(any(), any(), any(), any());
        verify(repository).createTaskEvent(
                any(),
                eq(taskId),
                eq(tenantId),
                eq(orderId),
                eq("CANCELLED"),
                eq("op-cancel-1"),
                eq("operator-1"),
                eq("{\"source\":\"pda-decoction-cancel\"}"),
                eq(Instant.now(clock))
        );
    }

    @Test
    void shouldRejectCancelWhenTaskAlreadyStarted() {
        UUID taskId = UUID.randomUUID();
        when(repository.findByCancelOperationId("op-cancel-1")).thenReturn(Optional.empty());
        when(repository.findByTaskNo("DCT-1"))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.DECOCTING.name())));

        assertThatThrownBy(() -> service.cancelMesTask(
                "DCT-1",
                eventCommand("op-cancel-1", "manual cancel", null, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only bound decoction task can be cancelled");
        verify(rejectedEventRecorder).recordRejected(
                eq(task(taskId, "DCT-1", DecoctionTaskStatus.DECOCTING.name())),
                eq("REJECTED-" + rejectedOperationId(taskId, "CANCEL", "op-cancel-1")),
                eq("operator-1"),
                eq("CANCEL"),
                eq("mes-decoction-cancel"),
                eq("DECOCTING"),
                eq("BOUND"),
                eq("{\"action\":\"CANCEL\",\"source\":\"mes-decoction-cancel\",\"originalOperationId\":\"op-cancel-1\","
                        + "\"actualStatus\":\"DECOCTING\",\"expectedStatus\":\"BOUND\","
                        + "\"reason\":\"Only bound decoction task can be cancelled\"}"),
                eq(Instant.now(clock))
        );
    }

    @Test
    void shouldTerminateDecoctingTaskAndCancelOrder() {
        UUID taskId = UUID.randomUUID();
        when(repository.findByTerminateOperationId("op-terminate-1")).thenReturn(Optional.empty());
        when(repository.findByTaskNo("DCT-1"))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.DECOCTING.name())));
        when(repository.markTerminated(taskId, "op-terminate-1", "operator-1")).thenReturn(1);
        when(repository.findByTaskId(taskId))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.TERMINATED.name())));

        DecoctionRecords.DecoctionTaskRecord result = service.terminateMesTask(
                "DCT-1",
                eventCommand("op-terminate-1", "manual stop", null, null, null)
        );

        assertThat(result.taskStatus()).isEqualTo("TERMINATED");
        verify(orderStatusClient).updateStatus(orderId, "CANCELLED", "DECOCTION", "mes-decoction-terminate");
        verify(repository).createTaskEvent(
                any(),
                eq(taskId),
                eq(tenantId),
                eq(orderId),
                eq("TERMINATED"),
                eq("op-terminate-1"),
                eq("operator-1"),
                eq("{\"source\":\"mes-decoction-terminate\",\"reason\":\"manual stop\"}"),
                eq(Instant.now(clock))
        );
    }

    @Test
    void shouldRejectTerminateBeforeTaskStarts() {
        UUID taskId = UUID.randomUUID();
        when(repository.findByTerminateOperationId("op-terminate-1")).thenReturn(Optional.empty());
        when(repository.findByTaskNo("DCT-1"))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.BOUND.name())));

        assertThatThrownBy(() -> service.terminateMesTask(
                "DCT-1",
                eventCommand("op-terminate-1", "manual stop", null, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only decocting task can be terminated");
        verify(rejectedEventRecorder).recordRejected(
                eq(task(taskId, "DCT-1", DecoctionTaskStatus.BOUND.name())),
                eq("REJECTED-" + rejectedOperationId(taskId, "TERMINATE", "op-terminate-1")),
                eq("operator-1"),
                eq("TERMINATE"),
                eq("mes-decoction-terminate"),
                eq("BOUND"),
                eq("DECOCTING"),
                eq("{\"action\":\"TERMINATE\",\"source\":\"mes-decoction-terminate\","
                        + "\"originalOperationId\":\"op-terminate-1\",\"actualStatus\":\"BOUND\","
                        + "\"expectedStatus\":\"DECOCTING\",\"reason\":\"Only decocting task can be terminated\"}"),
                eq(Instant.now(clock))
        );
    }

    @Test
    void shouldReturnExistingTaskForDuplicateTerminateOperation() {
        DecoctionTaskSnapshot existing = task(UUID.randomUUID(), "DCT-1", DecoctionTaskStatus.TERMINATED.name());
        when(repository.findByTerminateOperationId("op-terminate-1")).thenReturn(Optional.of(existing));

        DecoctionRecords.DecoctionTaskRecord result = service.terminateMesTask(
                "DCT-1",
                eventCommand("op-terminate-1", "manual stop", null, null, null)
        );

        assertThat(result.taskStatus()).isEqualTo("TERMINATED");
        verify(repository, never()).markTerminated(any(), any(), any());
        verify(repository, never()).createTaskEvent(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldListDeviceStatusFromActiveTasks() {
        properties.setDeviceCodes(List.of("DECOCT-001", "DECOCT-002"));
        when(repository.findActiveTasks()).thenReturn(List.of(
                task(UUID.randomUUID(), "DCT-1", DecoctionTaskStatus.DECOCTING.name())
        ));

        List<DecoctionRecords.DeviceRecord> result = service.listDevices();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().deviceStatus()).isEqualTo("WORKING");
        assertThat(result.get(1).deviceStatus()).isEqualTo("IDLE");
    }

    @Test
    void shouldListActiveMesTasks() {
        when(repository.findActiveTasks()).thenReturn(List.of(
                task(UUID.randomUUID(), "DCT-1", DecoctionTaskStatus.BOUND.name()),
                task(UUID.randomUUID(), "DCT-2", DecoctionTaskStatus.DECOCTING.name())
        ));

        List<DecoctionRecords.DecoctionTaskRecord> result = service.listActiveMesTasks();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DecoctionRecords.DecoctionTaskRecord::taskStatus)
                .containsExactly("BOUND", "DECOCTING");
    }

    @Test
    void shouldRecordWaterFinishedBeforeDecoctionStarts() {
        UUID taskId = UUID.randomUUID();
        DecoctionEventCommand command = eventCommand("op-water-1", null, 1200, null, null);
        when(repository.findEventByOperationId("op-water-1")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(event(taskId, "DCT-1", "WATER_FINISHED", "op-water-1")));
        when(repository.findByTaskNo("DCT-1"))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.BOUND.name())));

        DecoctionRecords.DecoctionTaskEventRecord result = service.recordWaterFinished("DCT-1", command);

        assertThat(result.eventType()).isEqualTo("WATER_FINISHED");
        verify(repository).createTaskEvent(
                any(),
                eq(taskId),
                eq(tenantId),
                eq(orderId),
                eq("WATER_FINISHED"),
                eq("op-water-1"),
                eq("operator-1"),
                eq("{\"waterVolumeMl\":1200}"),
                eq(Instant.now(clock))
        );
    }

    @Test
    void shouldRejectWaterFinishedAfterDecoctionStarts() {
        UUID taskId = UUID.randomUUID();
        when(repository.findEventByOperationId("op-water-1")).thenReturn(Optional.empty());
        when(repository.findByTaskNo("DCT-1"))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.DECOCTING.name())));

        assertThatThrownBy(() -> service.recordWaterFinished(
                "DCT-1",
                eventCommand("op-water-1", null, 1200, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Water can only be finished before decoction starts");
        verify(rejectedEventRecorder).recordRejected(
                eq(task(taskId, "DCT-1", DecoctionTaskStatus.DECOCTING.name())),
                eq("REJECTED-" + rejectedOperationId(taskId, "WATER_FINISHED", "op-water-1")),
                eq("operator-1"),
                eq("WATER_FINISHED"),
                eq("mes-event"),
                eq("DECOCTING"),
                eq("BOUND"),
                eq("{\"action\":\"WATER_FINISHED\",\"source\":\"mes-event\","
                        + "\"originalOperationId\":\"op-water-1\",\"actualStatus\":\"DECOCTING\","
                        + "\"expectedStatus\":\"BOUND\","
                        + "\"reason\":\"Water can only be finished before decoction starts\"}"),
                eq(Instant.now(clock))
        );
    }

    @Test
    void shouldRecordTemperatureWhileDecocting() {
        UUID taskId = UUID.randomUUID();
        DecoctionEventCommand command = eventCommand("op-temp-1", null, null, 98, 600);
        when(repository.findEventByOperationId("op-temp-1")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(event(taskId, "DCT-1", "TEMPERATURE", "op-temp-1")));
        when(repository.findByTaskNo("DCT-1"))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.DECOCTING.name())));

        DecoctionRecords.DecoctionTaskEventRecord result = service.recordTemperature("DCT-1", command);

        assertThat(result.eventType()).isEqualTo("TEMPERATURE");
        verify(repository).createTaskEvent(
                any(),
                eq(taskId),
                eq(tenantId),
                eq(orderId),
                eq("TEMPERATURE"),
                eq("op-temp-1"),
                eq("operator-1"),
                eq("{\"temperatureCelsius\":98,\"durationSeconds\":600}"),
                eq(Instant.now(clock))
        );
    }

    @Test
    void shouldRejectTemperatureWithoutValue() {
        assertThatThrownBy(() -> service.recordTemperature(
                "DCT-1",
                eventCommand("op-temp-1", null, null, null, 600)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Temperature is required");
    }

    @Test
    void shouldRecordErrorForAnyTaskStatus() {
        UUID taskId = UUID.randomUUID();
        DecoctionEventCommand command = eventCommand("op-error-1", "machine offline", null, null, null);
        when(repository.findEventByOperationId("op-error-1")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(event(taskId, "DCT-1", "ERROR", "op-error-1")));
        when(repository.findByTaskNo("DCT-1"))
                .thenReturn(Optional.of(task(taskId, "DCT-1", DecoctionTaskStatus.DECOCTED.name())));

        DecoctionRecords.DecoctionTaskEventRecord result = service.recordError("DCT-1", command);

        assertThat(result.eventType()).isEqualTo("ERROR");
        verify(repository).createTaskEvent(
                any(),
                eq(taskId),
                eq(tenantId),
                eq(orderId),
                eq("ERROR"),
                eq("op-error-1"),
                eq("operator-1"),
                eq("{\"reason\":\"machine offline\"}"),
                eq(Instant.now(clock))
        );
    }

    @Test
    void shouldReturnExistingEventForDuplicateOperation() {
        UUID taskId = UUID.randomUUID();
        when(repository.findEventByOperationId("op-water-1"))
                .thenReturn(Optional.of(event(taskId, "DCT-1", "WATER_FINISHED", "op-water-1")));

        DecoctionRecords.DecoctionTaskEventRecord result = service.recordWaterFinished(
                "DCT-1",
                eventCommand("op-water-1", null, 1200, null, null)
        );

        assertThat(result.operationId()).isEqualTo("op-water-1");
        verify(repository, never()).createTaskEvent(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private SimulatorOperationCommand command(String operationId, String deviceCode, String prescriptionNo) {
        return new SimulatorOperationCommand(
                operationId,
                deviceCode,
                prescriptionNo,
                "PAIL-1",
                "operator-1",
                Instant.now(clock),
                "dev-sign"
        );
    }

    private DecoctionEventCommand eventCommand(
            String operationId,
            String reason,
            Integer waterVolumeMl,
            Integer temperatureCelsius,
            Integer durationSeconds
    ) {
        return new DecoctionEventCommand(
                operationId,
                "operator-1",
                Instant.now(clock),
                "dev-sign",
                reason,
                waterVolumeMl,
                temperatureCelsius,
                durationSeconds,
                null
        );
    }

    private PrescriptionForDecoction prescription(String orderStatus) {
        return new PrescriptionForDecoction(
                tenantId,
                orderId,
                prescriptionId,
                "ZHYF1",
                "EXT1",
                "RX1",
                orderStatus
        );
    }

    private DecoctionTaskSnapshot task(UUID taskId, String taskNo, String status) {
        return new DecoctionTaskSnapshot(
                taskId,
                taskNo,
                tenantId,
                orderId,
                prescriptionId,
                "ZHYF1",
                "RX1",
                "DECOCT-001",
                "PAIL-1",
                status,
                "operator-1",
                null,
                null,
                Instant.now(clock),
                Instant.now(clock)
        );
    }

    private DecoctionTaskRepository.DecoctionTaskEventSnapshot event(
            UUID taskId,
            String taskNo,
            String eventType,
            String operationId
    ) {
        return new DecoctionTaskRepository.DecoctionTaskEventSnapshot(
                UUID.randomUUID(),
                taskId,
                taskNo,
                tenantId,
                orderId,
                eventType,
                operationId,
                "operator-1",
                "{}",
                Instant.now(clock),
                Instant.now(clock)
        );
    }

    private String rejectedOperationId(UUID taskId, String action, String operationId) {
        String raw = taskId + ":" + action + ":" + operationId;
        return UUID.nameUUIDFromBytes(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
