package com.zhyf.decoction.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.decoction.application.DecoctionRecords;
import com.zhyf.decoction.application.DecoctionSimulatorService;
import com.zhyf.decoction.application.SimulatorOperationCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SimulatorDeviceAdapterTest {

    private final DecoctionSimulatorService simulatorService = Mockito.mock(DecoctionSimulatorService.class);
    private final SimulatorDeviceAdapter adapter = new SimulatorDeviceAdapter(simulatorService);

    @Test
    void shouldMapLegacyStartStatusToPdaStartCommand() {
        when(simulatorService.startDecoction(any())).thenReturn(task("DECOCTING"));

        DecoctionRecords.DecoctionTaskRecord result = adapter.reportPdaStatus(request("1"));

        assertThat(result.taskStatus()).isEqualTo("DECOCTING");
        ArgumentCaptor<SimulatorOperationCommand> commandCaptor = ArgumentCaptor.forClass(SimulatorOperationCommand.class);
        verify(simulatorService).startDecoction(commandCaptor.capture());
        assertThat(commandCaptor.getValue().operationId()).isEqualTo("op-1");
        assertThat(commandCaptor.getValue().deviceCode()).isEqualTo("DECOCT-001");
        assertThat(commandCaptor.getValue().prescriptionNo()).isEqualTo("RX1");
    }

    @Test
    void shouldMapLegacyCancelStatusToPdaCancelCommand() {
        when(simulatorService.cancelDecoction(any())).thenReturn(task("CANCELLED"));

        DecoctionRecords.DecoctionTaskRecord result = adapter.reportPdaStatus(request("8"));

        assertThat(result.taskStatus()).isEqualTo("CANCELLED");
        verify(simulatorService).cancelDecoction(any());
    }

    private DeviceOperationRequest request(String status) {
        return new DeviceOperationRequest(
                "op-1",
                null,
                "DECOCT-001",
                "RX1",
                "PAIL-1",
                "operator-1",
                Instant.parse("2026-07-07T00:00:00Z"),
                "dev-sign",
                status,
                null,
                null,
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
}
