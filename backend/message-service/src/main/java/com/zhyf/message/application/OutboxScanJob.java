package com.zhyf.message.application;

import com.zhyf.message.infrastructure.OutboxEvent;
import com.zhyf.message.infrastructure.OutboxRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxScanJob {

    private final boolean enabled;
    private final OutboxRepository outboxRepository;
    private final OutboxPublisher outboxPublisher;

    public OutboxScanJob(
            @Value("${zhyf.message.outbox-scan-enabled:false}") boolean enabled,
            OutboxRepository outboxRepository,
            OutboxPublisher outboxPublisher
    ) {
        this.enabled = enabled;
        this.outboxRepository = outboxRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Scheduled(fixedDelay = 5000)
    public void scan() {
        if (!enabled) {
            return;
        }
        List<OutboxEvent> events = outboxRepository.fetchNewEvents(20);
        for (OutboxEvent event : events) {
            try {
                outboxPublisher.publish(event);
                outboxRepository.markSent(event.id());
            } catch (RuntimeException ex) {
                outboxRepository.markFailed(event.id());
            }
        }
    }
}
