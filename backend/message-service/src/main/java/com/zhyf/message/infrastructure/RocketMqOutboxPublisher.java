package com.zhyf.message.infrastructure;

import com.zhyf.message.application.OutboxPublisher;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "zhyf.message", name = "publisher-type", havingValue = "rocketmq")
public class RocketMqOutboxPublisher implements OutboxPublisher, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RocketMqOutboxPublisher.class);

    private final RocketMqProperties properties;
    private DefaultMQProducer producer;

    public RocketMqOutboxPublisher(RocketMqProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        producer = new DefaultMQProducer(properties.getProducerGroup());
        producer.setNamesrvAddr(properties.getNameServer());
        producer.start();
        log.info("RocketMQ producer started nameServer={} group={}",
                properties.getNameServer(), properties.getProducerGroup());
    }

    @Override
    public void publish(OutboxEvent event) {
        Message message = new Message(
                resolveTopic(event),
                event.eventType(),
                event.aggregateId(),
                event.payload().getBytes(StandardCharsets.UTF_8)
        );
        message.putUserProperty("eventId", event.eventId());
        message.putUserProperty("aggregateType", event.aggregateType());
        message.putUserProperty("aggregateId", event.aggregateId());
        try {
            SendResult result = producer.send(message);
            log.info("RocketMQ event sent eventId={} topic={} tag={} msgId={} status={}",
                    event.eventId(), message.getTopic(), message.getTags(), result.getMsgId(), result.getSendStatus());
        } catch (Exception ex) {
            throw new IllegalStateException("RocketMQ publish failed, eventId=" + event.eventId(), ex);
        }
    }

    @Override
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
        }
    }

    private String resolveTopic(OutboxEvent event) {
        return properties.getOrderTopic();
    }
}
