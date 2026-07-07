package com.zhyf.workflow.consumer;

import com.zhyf.workflow.application.OrderCreatedWorkflowService;
import com.zhyf.workflow.config.WorkflowProperties;
import com.zhyf.workflow.infrastructure.MessageConsumeRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "zhyf.workflow", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
public class RocketMqWorkflowConsumer implements InitializingBean, DisposableBean, MessageListenerConcurrently {

    private static final Logger log = LoggerFactory.getLogger(RocketMqWorkflowConsumer.class);

    private final WorkflowProperties properties;
    private final MessageConsumeRepository consumeRepository;
    private final OrderCreatedWorkflowService orderCreatedWorkflowService;
    private DefaultMQPushConsumer consumer;

    public RocketMqWorkflowConsumer(
            WorkflowProperties properties,
            MessageConsumeRepository consumeRepository,
            OrderCreatedWorkflowService orderCreatedWorkflowService
    ) {
        this.properties = properties;
        this.consumeRepository = consumeRepository;
        this.orderCreatedWorkflowService = orderCreatedWorkflowService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        consumer = new DefaultMQPushConsumer(properties.getRocketmq().getConsumerGroup());
        consumer.setNamesrvAddr(properties.getRocketmq().getNameServer());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setAllocateMessageQueueStrategy(new AllocateMessageQueueAveragely());
        consumer.subscribe(properties.getRocketmq().getOrderTopic(), "ORDER_CREATED");
        consumer.registerMessageListener(this);
        log.info("starting workflow RocketMQ consumer nameServer={} group={} topic={}",
                properties.getRocketmq().getNameServer(),
                properties.getRocketmq().getConsumerGroup(),
                properties.getRocketmq().getOrderTopic());
        consumer.start();
        log.info("workflow RocketMQ consumer started nameServer={} group={} topic={}",
                properties.getRocketmq().getNameServer(),
                properties.getRocketmq().getConsumerGroup(),
                properties.getRocketmq().getOrderTopic());
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages, ConsumeConcurrentlyContext context) {
        for (MessageExt message : messages) {
            String eventId = message.getUserProperty("eventId");
            String aggregateId = message.getUserProperty("aggregateId");
            if (!StringUtils.hasText(eventId)) {
                log.warn("skip workflow message without eventId msgId={} topic={}", message.getMsgId(), message.getTopic());
                continue;
            }
            if (!beginProcessing(eventId, message.getMsgId())) {
                continue;
            }
            try {
                orderCreatedWorkflowService.createReviewTaskIfValidationPassed(
                        eventId,
                        aggregateId,
                        new String(message.getBody(), StandardCharsets.UTF_8)
                );
                consumeRepository.markSuccess(properties.getRocketmq().getConsumerGroup(), eventId, message.getMsgId());
            } catch (RuntimeException ex) {
                consumeRepository.markFailed(properties.getRocketmq().getConsumerGroup(), eventId, message.getMsgId());
                log.error("workflow consume ORDER_CREATED failed eventId={} msgId={}", eventId, message.getMsgId(), ex);
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

    private boolean beginProcessing(String eventId, String messageId) {
        String consumerGroup = properties.getRocketmq().getConsumerGroup();
        if (!consumeRepository.tryBegin(consumerGroup, eventId, messageId)) {
            String status = consumeRepository.findStatus(consumerGroup, eventId).orElse("UNKNOWN");
            if ("SUCCESS".equalsIgnoreCase(status) || "PROCESSING".equalsIgnoreCase(status)) {
                return false;
            }
            return consumeRepository.markProcessing(consumerGroup, eventId, messageId) > 0;
        }
        return consumeRepository.markProcessing(consumerGroup, eventId, messageId) > 0;
    }
}
