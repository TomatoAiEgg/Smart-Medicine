package com.zhyf.message.application;

import com.zhyf.message.infrastructure.OutboxEvent;

public interface OutboxPublisher {

    void publish(OutboxEvent event);
}
