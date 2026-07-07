package com.zhyf.decoction.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.decoction.application.DecoctionEventCommand;
import com.zhyf.decoction.application.DecoctionRecords;
import com.zhyf.decoction.application.DecoctionSimulatorService;
import com.zhyf.decoction.application.MesTaskOperationCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SimulatorMesAdapterTest {

    private final DecoctionSimulatorService simulatorService = Mockito.mock(DecoctionSimulatorService.class);
    private final SimulatorMesAdapter adapter = new SimulatorMesAdapter(simulatorService);

    @Test
    void shouldResolveWaterNoticeTaskByPailNo() {
        when(simulatorService.getActiveTaskByPailNo("PAIL-1")).thenReturn(task("BOUND"));
        when(simulatorService.recordWaterFinished(eq("DCT-1"), any())).thenReturn(event("WATER_FINISHED"));

        DecoctionRecords.DecoctionTaskEventRecord result = adapter.waterEndNotice(request(null, "PAIL-1", null, 1200));

        assertThat(result.eventType()).isEqualTo("WATER_FINISHED");
        ArgumentCaptor<DecoctionEventCommand> commandCaptor = ArgumentCaptor.forClass(DecoctionEventCommand.class);
        verify(simulatorService).recordWaterFinished(eq("DCT-1"), commandCaptor.capture());
        assertThat(commandCaptor.getValue().waterVolumeMl()).isEqualTo(1200);
    }

    @Test
    void shouldMapLegacyFinishStatusToMesFinishCommand() {
        when(simulatorService.finishMesTask(eq("DCT-1"), any())).thenReturn(task("DECOCTED"));

        DecoctionRecords.DecoctionTaskRecord result = adapter.decoctingStatusNotice(request("DCT-1", null, "2", null));

        assertThat(result.taskStatus()).isEqualTo("DECOCTED");
        ArgumentCaptor<MesTaskOperationCommand> commandCaptor = ArgumentCaptor.forClass(MesTaskOperationCommand.class);
        verify(simulatorService).finishMesTask(eq("DCT-1"), commandCaptor.capture());
        assertThat(commandCaptor.getValue().operationId()).isEqualTo("op-1");
    }

    private DeviceOperationRequest request(String taskNo, String pailNo, String status, Integer waterVolumeMl) {
        return new DeviceOperationRequest(
                "op-1",
                taskNo,
                "DECOCT-001",
                "RX1",
                pailNo,
                "operator-1",
                Instant.parse("2026-07-07T00:00:00Z"),
                "dev-sign",
                status,
                null,
                waterVolumeMl,
                null,
                null,
                null
        );
    }

    private DecoctionRecords.DecoctionTaskRecord task(String status) {
        return new DecoctionRecords.DecoctionTaskRecord(
                UUID.randomUUID(),
                "DCT-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ZHYF1",
                "RX1",
                "DECOCT-001",
                "PAIL-1",
                status,
                "operator-1",
                null,
                null,
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-07T00:00:00Z")
        );
    }

    private DecoctionRecords.DecoctionTaskEventRecord event(String eventType) {
        return new DecoctionRecords.DecoctionTaskEventRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DCT-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventType,
                "op-1",
                "operator-1",
                "{}",
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-07T00:00:00Z")
        );
    }
}
