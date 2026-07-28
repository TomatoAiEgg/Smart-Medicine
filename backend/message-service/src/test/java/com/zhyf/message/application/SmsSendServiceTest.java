package com.zhyf.message.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.message.infrastructure.SmsSendRecordRepository;
import com.zhyf.message.infrastructure.SmsTemplateRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SmsSendServiceTest {

    private static final UUID TEMPLATE_ID = UUID.fromString("11111111-2222-3333-4444-000000000601");
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final SmsTemplateRepository templateRepository = Mockito.mock(SmsTemplateRepository.class);
    private final SmsSendRecordRepository sendRecordRepository = Mockito.mock(SmsSendRecordRepository.class);
    private final SmsSendService service = new SmsSendService(templateRepository, sendRecordRepository);

    @Test
    void shouldRenderTemplateAndInsertSimulatedSendRecord() {
        SmsTemplateRecords.SmsTemplateRecord template = template(true);
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(sendRecordRepository.insertSingleSendRecord(
                any(UUID.class),
                eq(template),
                eq("13800138000"),
                eq("张三"),
                eq("ORD001"),
                eq("订单ORD001状态待审核"),
                eq(Map.of("orderNo", "ORD001", "orderStatus", "待审核")),
                eq("SIMULATED"),
                eq("admin")
        )).thenReturn(sendResult());

        service.sendSingleSms(new SmsSendRecords.SmsSendCommand(
                TEMPLATE_ID,
                " 13800138000 ",
                " 张三 ",
                " ORD001 ",
                Map.of("orderNo", "ORD001", "orderStatus", "待审核"),
                " admin "
        ));

        verify(sendRecordRepository).insertSingleSendRecord(
                any(UUID.class),
                eq(template),
                eq("13800138000"),
                eq("张三"),
                eq("ORD001"),
                eq("订单ORD001状态待审核"),
                eq(Map.of("orderNo", "ORD001", "orderStatus", "待审核")),
                eq("SIMULATED"),
                eq("admin")
        );
    }

    @Test
    void shouldRejectDisabledTemplate() {
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(false)));

        assertThatThrownBy(() -> service.sendSingleSms(new SmsSendRecords.SmsSendCommand(
                TEMPLATE_ID,
                "13800138000",
                null,
                null,
                Map.of(),
                null
        ))).isInstanceOf(BusinessException.class);
    }

    private SmsTemplateRecords.SmsTemplateRecord template(boolean enabled) {
        return new SmsTemplateRecords.SmsTemplateRecord(
                TEMPLATE_ID,
                TENANT_ID,
                "order-created-notice",
                "订单创建通知",
                "ORDER",
                "订单{{orderNo}}状态{{orderStatus}}",
                "智慧药房",
                enabled,
                Instant.parse("2026-07-28T00:00:00Z"),
                Instant.parse("2026-07-28T00:00:00Z")
        );
    }

    private SmsSendRecords.SmsSendResult sendResult() {
        return new SmsSendRecords.SmsSendResult(
                UUID.randomUUID(),
                TENANT_ID,
                TEMPLATE_ID,
                "order-created-notice",
                "订单创建通知",
                "13800138000",
                "张三",
                "ORD001",
                "智慧药房",
                "订单ORD001状态待审核",
                "SIMULATED",
                null,
                null,
                0,
                "admin",
                Instant.parse("2026-07-28T00:00:00Z"),
                Instant.parse("2026-07-28T00:00:00Z"),
                null
        );
    }
}
