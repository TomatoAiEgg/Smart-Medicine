package com.zhyf.decoction.api.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhyf.decoction.adapter.DeviceOperationRequest;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyDeviceRequestTest {

    @Test
    void shouldReadLegacyFieldAliasesFromQueryAndBody() {
        LegacyDeviceRequest request = LegacyDeviceRequest.from(
                Map.of("operStatus", "1"),
                Map.of(
                        "recipeId", "RX1",
                        "equipCode", "DECOCT-001",
                        "barrelNo", "PAIL-1",
                        "account", "operator-1",
                        "opTime", "2026-07-07T00:00:00Z",
                        "water", "1200"
                )
        );

        DeviceOperationRequest result = request.toOperation("LEGACY-PDA-STATUS");

        assertThat(result.prescriptionNo()).isEqualTo("RX1");
        assertThat(result.deviceCode()).isEqualTo("DECOCT-001");
        assertThat(result.pailNo()).isEqualTo("PAIL-1");
        assertThat(result.operator()).isEqualTo("operator-1");
        assertThat(result.status()).isEqualTo("1");
        assertThat(result.waterVolumeMl()).isEqualTo(1200);
        assertThat(result.timestamp()).isEqualTo(Instant.parse("2026-07-07T00:00:00Z"));
        assertThat(result.operationId()).contains("LEGACY-PDA-STATUS-RX1-1-2026-07-07T00:00:00Z");
    }
}
