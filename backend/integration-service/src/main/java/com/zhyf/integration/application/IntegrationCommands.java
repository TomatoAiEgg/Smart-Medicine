package com.zhyf.integration.application;

import java.util.UUID;

public final class IntegrationCommands {

    private IntegrationCommands() {
    }

    public record CommunityMessageCommand(
            String areaCode,
            String communityCode,
            String externalMessageId,
            String messageType,
            String businessKey,
            String rawPayload
    ) {
    }

    public record AddressPushCommand(
            UUID supplementId,
            String hospitalCode,
            String adapterCode,
            String orderNo,
            String rawPayload
    ) {
    }
}
