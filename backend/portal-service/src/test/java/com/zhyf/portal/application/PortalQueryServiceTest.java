package com.zhyf.portal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.portal.infrastructure.PortalRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PortalQueryServiceTest {

    private final PortalRepository repository = Mockito.mock(PortalRepository.class);
    private final PortalQueryService service = new PortalQueryService(repository, new ObjectMapper());

    @Test
    void shouldRequirePhoneWhenQueryingOrder() {
        PortalCommands.PortalOrderQuery query = new PortalCommands.PortalOrderQuery(
                "ZHYF1",
                null,
                " "
        );

        assertThatThrownBy(() -> service.queryOrder(query))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Query phone is required");
    }

    @Test
    void shouldQueryOrderByOrderNoAndPhone() {
        PortalRecords.PortalOrderRecord order = orderRecord();
        PortalCommands.PortalOrderQuery query = new PortalCommands.PortalOrderQuery(
                "ZHYF1",
                null,
                "13800138000"
        );
        when(repository.findOrderForPortal("ZHYF1", null, "13800138000")).thenReturn(Optional.of(order));

        PortalRecords.PortalOrderRecord result = service.queryOrder(query);

        assertThat(result.orderNo()).isEqualTo("ZHYF1");
        assertThat(result.prescriptions()).hasSize(1);
        verify(repository).findOrderForPortal("ZHYF1", null, "13800138000");
    }

    @Test
    void shouldCreatePendingAddressSupplement() {
        PortalRecords.PortalOrderRecord order = orderRecord();
        PortalCommands.AddressSupplementCommand command = new PortalCommands.AddressSupplementCommand(
                "13800138000",
                "张三",
                "13800138000",
                "广东省",
                "深圳市",
                "南山区",
                "科技园一号",
                "张三",
                "13800138000",
                "医院补录地址"
        );
        PortalRecords.AddressSupplementRecord supplement = new PortalRecords.AddressSupplementRecord(
                UUID.randomUUID(),
                order.tenantId(),
                order.orderId(),
                order.orderNo(),
                "PENDING",
                command.receiverName(),
                command.receiverPhone(),
                command.receiverProvince(),
                command.receiverCity(),
                command.receiverZone(),
                command.receiverAddress(),
                command.requesterName(),
                command.requesterPhone(),
                command.remark(),
                Instant.parse("2026-07-09T08:00:00Z")
        );
        when(repository.findOrderForPortal("ZHYF1", null, "13800138000")).thenReturn(Optional.of(order));
        when(repository.createAddressSupplement(any(UUID.class), eq(order), eq(command), any(String.class)))
                .thenReturn(supplement);

        PortalRecords.AddressSupplementRecord result = service.createAddressSupplement("ZHYF1", command);

        assertThat(result.supplementStatus()).isEqualTo("PENDING");
        verify(repository).createAddressSupplement(any(UUID.class), eq(order), eq(command), any(String.class));
    }

    private PortalRecords.PortalOrderRecord orderRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        return new PortalRecords.PortalOrderRecord(
                tenantId,
                orderId,
                "演示医院",
                "ZHYF1",
                "EXT1",
                "SIGNED",
                "张三",
                "13800138000",
                "张三",
                "13800138000",
                "广东省深圳市南山区科技园",
                Instant.parse("2026-07-09T07:00:00Z"),
                List.of(new PortalRecords.PrescriptionRecord("RX1", "DECOCTED", "中药处方", "李医生", "感冒")),
                new PortalRecords.ShipmentRecord("SF1", "SF", "SIGNED", Instant.parse("2026-07-09T07:30:00Z"))
        );
    }
}
