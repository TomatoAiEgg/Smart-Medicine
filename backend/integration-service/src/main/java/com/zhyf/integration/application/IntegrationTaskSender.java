package com.zhyf.integration.application;

public interface IntegrationTaskSender {

    IntegrationSendResult send(IntegrationRecords.IntegrationRetryTaskRecord task);
}
