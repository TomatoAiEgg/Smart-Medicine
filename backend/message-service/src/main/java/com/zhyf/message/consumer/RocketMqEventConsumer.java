package com.zhyf.message.consumer;

import com.zhyf.message.application.MessageEvent;
import com.zhyf.message.application.MessageEventHandler;
import com.zhyf.message.infrastructure.MessageConsumeRepository;
import com.zhyf.message.infrastructure.RocketMqProperties;
import java.util.List;
import java.util.Optional;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "zhyf.message", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
public class RocketMqEventConsumer implements InitializingBean, DisposableBean, MessageListenerConcurrently {

    private static final Logger log = LoggerFactory.getLogger(RocketMqEventConsumer.class);

    private final RocketMqProperties properties;
    private final MessageConsumeRepository consumeRepository;
    private final List<MessageEventHandler> handlers;
    private DefaultMQPushConsumer consumer;

    public RocketMqEventConsumer(
            RocketMqProperties properties,
            MessageConsumeRepository consumeRepository,
            List<MessageEventHandler> handlers
    ) {
        this.properties = properties;
        this.consumeRepository = consumeRepository;
        this.handlers = handlers;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        consumer = new DefaultMQPushConsumer(properties.getConsumerGroup());
        consumer.setNamesrvAddr(properties.getNameServer());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setAllocateMessageQueueStrategy(new AllocateMessageQueueAveragely());
        consumer.subscribe(properties.getOrderTopic(), "*");
        consumer.registerMessageListener(this);
        log.info("starting RocketMQ consumer nameServer={} group={} topic={}",
                properties.getNameServer(), properties.getConsumerGroup(), properties.getOrderTopic());
        consumer.start();
        log.info("RocketMQ consumer started nameServer={} group={} topic={}",
                properties.getNameServer(), properties.getConsumerGroup(), properties.getOrderTopic());
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages, ConsumeConcurrentlyContext context) {
        for (MessageExt message : messages) {
            String eventId = message.getUserProperty("eventId");
            String eventType = message.getTags();
            String aggregateType = message.getUserProperty("aggregateType");
            String aggregateId = message.getUserProperty("aggregateId");
            if (!org.springframework.util.StringUtils.hasText(eventId)) {
                log.warn("skip rocketmq message without eventId msgId={} topic={}", message.getMsgId(), message.getTopic());
                continue;
            }

            if (!consumeRepository.tryBegin(properties.getConsumerGroup(), eventId, message.getMsgId())) {
                String status = consumeRepository.findStatus(properties.getConsumerGroup(), eventId).orElse("UNKNOWN");
                if ("SUCCESS".equalsIgnoreCase(status)) {
                    continue;
                }
                if ("PROCESSING".equalsIgnoreCase(status)) {
                    continue;
                }
                if (consumeRepository.markProcessing(properties.getConsumerGroup(), eventId, message.getMsgId()) == 0) {
                    continue;
                }
            } else if (consumeRepository.markProcessing(properties.getConsumerGroup(), eventId, message.getMsgId()) == 0) {
                continue;
            }

            MessageEvent event = new MessageEvent(
                    eventId,
                    eventType,
                    aggregateType,
                    aggregateId,
                    message.getMsgId(),
                    new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8)
            );

            try {
                dispatch(event);
                consumeRepository.markSuccess(properties.getConsumerGroup(), eventId, message.getMsgId());
            } catch (RuntimeException ex) {
                consumeRepository.markFailed(properties.getConsumerGroup(), eventId, message.getMsgId());
                log.error("consume rocketmq message failed eventId={} msgId={}", eventId, message.getMsgId(), ex);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    @Override
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }

    private void dispatch(MessageEvent event) {
        Optional<MessageEventHandler> handler = handlers.stream()
                .filter(candidate -> candidate.supports(event.eventType()))
                .findFirst();
        if (handler.isEmpty()) {
            log.info("no handler for eventType={} eventId={} aggregateId={}",
                    event.eventType(), event.eventId(), event.aggregateId());
            return;
        }
        handler.get().handle(event);
    }
}
