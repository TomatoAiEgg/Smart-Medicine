package com.zhyf.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.integration.infrastructure.IntegrationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IntegrationServiceTest {

    private final IntegrationRepository repository = Mockito.mock(IntegrationRepository.class);
    private final IntegrationTaskSender sender = Mockito.mock(IntegrationTaskSender.class);
    private final IntegrationProperties properties = new IntegrationProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-09T08:00:00Z"), ZoneOffset.UTC);
    private final IntegrationService service = new IntegrationService(repository, sender, properties, clock);

    @Test
    void shouldRequireExternalMessageIdForCommunityMessage() {
        IntegrationCommands.CommunityMessageCommand command = new IntegrationCommands.CommunityMessageCommand(
                "LG",
                "CH-001",
                " ",
                "ORDER_CREATED",
                "CH-ORDER-1",
                "{\"id\":\"CH-ORDER-1\"}"
        );

        assertThatThrownBy(() -> service.recordCommunityMessage(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("External message id is required");
    }

    @Test
    void shouldCreatePendingCommunityMessage() {
        IntegrationCommands.CommunityMessageCommand command = new IntegrationCommands.CommunityMessageCommand(
                " LG ",
                " CH-001 ",
                " MSG-001 ",
                " ORDER_CREATED ",
                " CH-ORDER-1 ",
                "{\"id\":\"CH-ORDER-1\"}"
        );
        IntegrationRecords.IntegrationMessageRecord created = messageRecord(
                "COMMUNITY_HOSPITAL",
                "CH-001",
                "MSG-001",
                "ORDER_CREATED",
                "CH-ORDER-1"
        );
        when(repository.findMessage("COMMUNITY_HOSPITAL", "CH-001", "MSG-001")).thenReturn(Optional.empty());
        when(repository.createMessage(any(UUID.class), eq("COMMUNITY_HOSPITAL"), eq("CH-001"), eq("MSG-001"),
                eq("ORDER_CREATED"), eq("CH-ORDER-1"), any(String.class), any(String.class))).thenReturn(created);

        IntegrationRecords.IntegrationMessageRecord result = service.recordCommunityMessage(command);

        assertThat(result.processStatus()).isEqualTo("PENDING");
        verify(repository).findMessage("COMMUNITY_HOSPITAL", "CH-001", "MSG-001");
        verify(repository).createMessage(any(UUID.class), eq("COMMUNITY_HOSPITAL"), eq("CH-001"), eq("MSG-001"),
                eq("ORDER_CREATED"), eq("CH-ORDER-1"), any(String.class), any(String.class));
    }

    @Test
    void shouldAllowBlankOptionalCommunityAreaAndBusinessKey() {
        IntegrationCommands.CommunityMessageCommand command = new IntegrationCommands.CommunityMessageCommand(
                " ",
                " CH-001 ",
                " MSG-002 ",
                " ORDER_CREATED ",
                " ",
                "{}"
        );
        IntegrationRecords.IntegrationMessageRecord created = messageRecord(
                "COMMUNITY_HOSPITAL",
                "CH-001",
                "MSG-002",
                "ORDER_CREATED",
                null
        );
        when(repository.findMessage("COMMUNITY_HOSPITAL", "CH-001", "MSG-002")).thenReturn(Optional.empty());
        when(repository.createMessage(any(UUID.class), eq("COMMUNITY_HOSPITAL"), eq("CH-001"), eq("MSG-002"),
                eq("ORDER_CREATED"), isNull(), argThat(payload -> payload.contains("communityCode")
                        && !payload.contains("areaCode") && !payload.contains("businessKey")), any(String.class)))
                .thenReturn(created);

        IntegrationRecords.IntegrationMessageRecord result = service.recordCommunityMessage(command);

        assertThat(result.externalMessageId()).isEqualTo("MSG-002");
    }

    @Test
    void shouldReturnExistingCommunityMessageWhenDuplicate() {
        IntegrationCommands.CommunityMessageCommand command = new IntegrationCommands.CommunityMessageCommand(
                "LG",
                "CH-001",
                "MSG-001",
                "ORDER_CREATED",
                "CH-ORDER-1",
                "{}"
        );
        IntegrationRecords.IntegrationMessageRecord existing = messageRecord(
                "COMMUNITY_HOSPITAL",
                "CH-001",
                "MSG-001",
                "ORDER_CREATED",
                "CH-ORDER-1"
        );
        when(repository.findMessage("COMMUNITY_HOSPITAL", "CH-001", "MSG-001")).thenReturn(Optional.of(existing));

        IntegrationRecords.IntegrationMessageRecord result = service.recordCommunityMessage(command);

        assertThat(result.messageId()).isEqualTo(existing.messageId());
    }

    @Test
    void shouldCreatePendingAddressPushRecord() {
        UUID supplementId = UUID.randomUUID();
        IntegrationCommands.AddressPushCommand command = new IntegrationCommands.AddressPushCommand(
                supplementId,
                "HOSP-001",
                "LGFY",
                "ZHYF1",
                "{\"receiver\":\"张三\"}",
                null
        );
        IntegrationRecords.IntegrationMessageRecord created = messageRecord(
                "ADDRESS_PUSH",
                "HOSP-001",
                supplementId.toString(),
                "LGFY",
                "ZHYF1"
        );
        when(repository.findMessage("ADDRESS_PUSH", "HOSP-001", supplementId.toString())).thenReturn(Optional.empty());
        when(repository.createMessage(any(UUID.class), eq("ADDRESS_PUSH"), eq("HOSP-001"), eq(supplementId.toString()),
                eq("LGFY"), eq("ZHYF1"), any(String.class), any(String.class))).thenReturn(created);

        IntegrationRecords.IntegrationMessageRecord result = service.recordAddressPush(command);

        assertThat(result.sourceType()).isEqualTo("ADDRESS_PUSH");
        assertThat(result.processStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldCreateRetryTaskWhenAddressPushHasRequestUrl() {
        UUID supplementId = UUID.randomUUID();
        IntegrationCommands.AddressPushCommand command = new IntegrationCommands.AddressPushCommand(
                supplementId,
                "HOSP-001",
                "LGFY",
                "ZHYF1",
                "{\"receiver\":\"张三\"}",
                " http://127.0.0.1:19999/address "
        );
        IntegrationRecords.IntegrationMessageRecord created = messageRecord(
                "ADDRESS_PUSH",
                "HOSP-001",
                supplementId.toString(),
                "LGFY",
                "ZHYF1"
        );
        IntegrationRecords.IntegrationRetryTaskRecord retryTask = retryTaskRecord(
                created.messageId(),
                "ADDRESS_PUSH",
                "HOSP-001",
                "ZHYF1",
                "http://127.0.0.1:19999/address",
                "{\"receiver\":\"张三\"}",
                "PENDING",
                0
        );
        when(repository.findMessage("ADDRESS_PUSH", "HOSP-001", supplementId.toString())).thenReturn(Optional.empty());
        when(repository.createMessage(any(UUID.class), eq("ADDRESS_PUSH"), eq("HOSP-001"), eq(supplementId.toString()),
                eq("LGFY"), eq("ZHYF1"), any(String.class), any(String.class))).thenReturn(created);
        when(repository.findRetryTask(created.messageId(), "ADDRESS_PUSH")).thenReturn(Optional.empty());
        when(repository.createRetryTask(any(UUID.class), eq(created.messageId()), eq("ADDRESS_PUSH"), eq("HOSP-001"),
                eq("ZHYF1"), eq("http://127.0.0.1:19999/address"), eq("{\"receiver\":\"张三\"}"), any()))
                .thenReturn(retryTask);

        IntegrationRecords.IntegrationMessageRecord result = service.recordAddressPush(command);

        assertThat(result.messageId()).isEqualTo(created.messageId());
        verify(repository).createRetryTask(any(UUID.class), eq(created.messageId()), eq("ADDRESS_PUSH"), eq("HOSP-001"),
                eq("ZHYF1"), eq("http://127.0.0.1:19999/address"), eq("{\"receiver\":\"张三\"}"), any());
    }

    @Test
    void shouldCreateCommunityStatusRetryTask() {
        IntegrationCommands.CommunityStatusPushCommand command = new IntegrationCommands.CommunityStatusPushCommand(
                "CH-001",
                "ZHYF1",
                "SIGNED",
                "http://127.0.0.1:19999/community/status",
                "{\"orderNo\":\"ZHYF1\",\"status\":\"SIGNED\"}"
        );
        IntegrationRecords.IntegrationMessageRecord created = messageRecord(
                "COMMUNITY_STATUS_PUSH",
                "CH-001",
                "ZHYF1:SIGNED",
                "SIGNED",
                "ZHYF1"
        );
        when(repository.findMessage("COMMUNITY_STATUS_PUSH", "CH-001", "ZHYF1:SIGNED")).thenReturn(Optional.empty());
        when(repository.createMessage(any(UUID.class), eq("COMMUNITY_STATUS_PUSH"), eq("CH-001"), eq("ZHYF1:SIGNED"),
                eq("SIGNED"), eq("ZHYF1"), any(String.class), any(String.class))).thenReturn(created);
        when(repository.findRetryTask(created.messageId(), "COMMUNITY_STATUS_PUSH")).thenReturn(Optional.empty());
        when(repository.createRetryTask(any(UUID.class), eq(created.messageId()), eq("COMMUNITY_STATUS_PUSH"),
                eq("CH-001"), eq("ZHYF1"), eq("http://127.0.0.1:19999/community/status"),
                eq("{\"orderNo\":\"ZHYF1\",\"status\":\"SIGNED\"}"), any()))
                .thenReturn(retryTaskRecord(created.messageId(), "COMMUNITY_STATUS_PUSH", "CH-001", "ZHYF1",
                        "http://127.0.0.1:19999/community/status", "{}", "PENDING", 0));

        IntegrationRecords.IntegrationMessageRecord result = service.createCommunityStatusPush(command);

        assertThat(result.sourceType()).isEqualTo("COMMUNITY_STATUS_PUSH");
        verify(repository).createRetryTask(any(UUID.class), eq(created.messageId()), eq("COMMUNITY_STATUS_PUSH"),
                eq("CH-001"), eq("ZHYF1"), eq("http://127.0.0.1:19999/community/status"),
                eq("{\"orderNo\":\"ZHYF1\",\"status\":\"SIGNED\"}"), any());
    }

    @Test
    void shouldDispatchRetryTaskSuccessfully() {
        IntegrationRecords.IntegrationRetryTaskRecord task = retryTaskRecord(
                UUID.randomUUID(),
                "ADDRESS_PUSH",
                "HOSP-001",
                "ZHYF1",
                "http://127.0.0.1:19999/address",
                "{}",
                "PENDING",
                0
        );
        when(repository.findDueRetryTasks(Instant.now(clock), 20)).thenReturn(List.of(task));
        when(sender.send(task)).thenReturn(IntegrationSendResult.success("ok"));

        int handled = service.dispatchDueRetryTasks(20);

        assertThat(handled).isEqualTo(1);
        verify(repository).markRetryTaskSucceeded(task.taskId(), "ok");
        verify(repository).markMessageStatus(task.messageId(), "SUCCESS", null);
    }

    @Test
    void shouldScheduleRetryWhenRetryTaskSendFailsBeforeMaxRetries() {
        properties.setMaxRetries(3);
        IntegrationRecords.IntegrationRetryTaskRecord task = retryTaskRecord(
                UUID.randomUUID(),
                "ADDRESS_PUSH",
                "HOSP-001",
                "ZHYF1",
                "http://127.0.0.1:19999/address",
                "{}",
                "PENDING",
                0
        );
        when(repository.findDueRetryTasks(Instant.now(clock), 20)).thenReturn(List.of(task));
        when(sender.send(task)).thenReturn(IntegrationSendResult.failure("http 500"));

        int handled = service.dispatchDueRetryTasks(20);

        assertThat(handled).isEqualTo(1);
        verify(repository).markRetryTaskFailed(task.taskId(), "http 500",
                Instant.now(clock).plus(60, ChronoUnit.SECONDS));
        verify(repository).markMessageStatus(task.messageId(), "FAILED", "http 500");
    }

    @Test
    void shouldMarkRetryTaskDeadWhenSendFailsAtMaxRetries() {
        properties.setMaxRetries(3);
        IntegrationRecords.IntegrationRetryTaskRecord task = retryTaskRecord(
                UUID.randomUUID(),
                "ADDRESS_PUSH",
                "HOSP-001",
                "ZHYF1",
                "http://127.0.0.1:19999/address",
                "{}",
                "FAILED",
                2
        );
        when(repository.findDueRetryTasks(Instant.now(clock), 20)).thenReturn(List.of(task));
        when(sender.send(task)).thenReturn(IntegrationSendResult.failure("timeout"));

        int handled = service.dispatchDueRetryTasks(20);

        assertThat(handled).isEqualTo(1);
        verify(repository).markRetryTaskDead(task.taskId(), "timeout");
        verify(repository).markMessageStatus(task.messageId(), "DEAD", "timeout");
    }

    @Test
    void shouldRequirePhoneWhenQueryingHospitalOrderByPrescription() {
        assertThatThrownBy(() -> service.findHospitalOrderByPrescription("RX1", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Phone is required");
    }

    private IntegrationRecords.IntegrationMessageRecord messageRecord(
            String sourceType,
            String sourceSystem,
            String externalMessageId,
            String messageType,
            String businessKey
    ) {
        return new IntegrationRecords.IntegrationMessageRecord(
                UUID.randomUUID(),
                sourceType,
                sourceSystem,
                externalMessageId,
                messageType,
                businessKey,
                "PENDING",
                "{}",
                "{}",
                null,
                Instant.parse("2026-07-09T07:00:00Z"),
                Instant.parse("2026-07-09T07:00:00Z"),
                null
        );
    }

    private IntegrationRecords.IntegrationRetryTaskRecord retryTaskRecord(
            UUID messageId,
            String taskType,
            String targetSystem,
            String businessKey,
            String requestUrl,
            String requestBody,
            String taskStatus,
            int retryCount
    ) {
        return new IntegrationRecords.IntegrationRetryTaskRecord(
                UUID.randomUUID(),
                messageId,
                taskType,
                targetSystem,
                businessKey,
                requestUrl,
                requestBody,
                null,
                taskStatus,
                retryCount,
                null,
                Instant.parse("2026-07-09T08:00:00Z"),
                Instant.parse("2026-07-09T08:00:00Z"),
                null
        );
    }
}
