package com.zhyf.logistics.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.logistics.application.LogisticsRecords;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class LogisticsRepositoryTest {

    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final LogisticsRepository repository = new LogisticsRepository(jdbcTemplate);

    @Test
    void shouldConvertPackageTimeToOffsetDateTimeWhenCreatingShipment() {
        Instant packageTime = Instant.parse("2026-07-08T00:00:00Z");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.createShipment(
                UUID.randomUUID(),
                order(),
                "SF-1",
                "SF",
                "MONTHLY",
                new BigDecimal("1.20"),
                1,
                packageTime
        );

        Object[] args = capturedUpdateArgs();
        assertThat(args[9]).isEqualTo(OffsetDateTime.ofInstant(packageTime, ZoneOffset.UTC));
    }

    @Test
    void shouldConvertActionTimeToOffsetDateTimeWhenUpdatingShipmentStatus() {
        Instant actionTime = Instant.parse("2026-07-08T01:00:00Z");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.updateShipmentStatus(UUID.randomUUID(), "SHIPPED", actionTime);

        Object[] args = capturedUpdateArgs();
        assertThat(args[1]).isEqualTo(OffsetDateTime.ofInstant(actionTime, ZoneOffset.UTC));
    }

    @Test
    void shouldConvertTraceTimeToOffsetDateTimeWhenCreatingTrace() {
        Instant traceTime = Instant.parse("2026-07-08T02:00:00Z");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.createTrace(UUID.randomUUID(), shipment(), "IN_TRANSIT", "shipping", "{}", traceTime);

        Object[] args = capturedUpdateArgs();
        assertThat(args[8]).isEqualTo(OffsetDateTime.ofInstant(traceTime, ZoneOffset.UTC));
    }

    private Object[] capturedUpdateArgs() {
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        return captor.getValue();
    }

    private LogisticsRecords.DeliveryOrderRecord order() {
        return new LogisticsRecords.DeliveryOrderRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ZHYF1",
                "EXT1",
                "DECOCTED",
                "receiver",
                "13800000000",
                "address"
        );
    }

    private LogisticsRecords.ShipmentRecord shipment() {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        return new LogisticsRecords.ShipmentRecord(
                UUID.randomUUID(),
                tenantId,
                orderId,
                "ZHYF1",
                "SF-1",
                "SF",
                "PACKED",
                "MONTHLY",
                new BigDecimal("1.20"),
                1,
                null,
                null,
                null,
                Instant.parse("2026-07-08T00:00:00Z"),
                Instant.parse("2026-07-08T00:00:00Z")
        );
    }
}
