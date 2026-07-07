package com.zhyf.message.publisher;

import com.zhyf.message.application.OutboxPublisher;
import com.zhyf.message.infrastructure.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "zhyf.message", name = "publisher-type", havingValue = "logging", matchIfMissing = true)
public class LoggingOutboxPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingOutboxPublisher.class);

    @Override
    public void publish(OutboxEvent event) {
        log.info("publish outbox event eventId={} eventType={} aggregateType={} aggregateId={}",
                event.eventId(), event.eventType(), event.aggregateType(), event.aggregateId());
    }
}
