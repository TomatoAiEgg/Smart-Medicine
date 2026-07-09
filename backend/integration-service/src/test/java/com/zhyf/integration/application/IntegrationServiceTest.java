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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IntegrationServiceTest {

    private final IntegrationRepository repository = Mockito.mock(IntegrationRepository.class);
    private final IntegrationService service = new IntegrationService(repository);

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
                "{\"receiver\":\"张三\"}"
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
}
