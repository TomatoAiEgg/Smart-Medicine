package com.zhyf.message.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "zhyf.message.rocketmq")
public class RocketMqProperties {

    private static final String DEFAULT_NAME_SERVER = "100.115.165.51:9876";
    private static final String DEFAULT_PRODUCER_GROUP = "zhyf-message-producer";
    private static final String DEFAULT_CONSUMER_GROUP = "zhyf-message-order-consumer";
    private static final String DEFAULT_ORDER_TOPIC = "zhyf-order-event";

    private String nameServer = DEFAULT_NAME_SERVER;
    private String producerGroup = DEFAULT_PRODUCER_GROUP;
    private String consumerGroup = DEFAULT_CONSUMER_GROUP;
    private String orderTopic = DEFAULT_ORDER_TOPIC;

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        this.nameServer = defaultIfBlank(nameServer, DEFAULT_NAME_SERVER);
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = defaultIfBlank(producerGroup, DEFAULT_PRODUCER_GROUP);
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = defaultIfBlank(consumerGroup, DEFAULT_CONSUMER_GROUP);
    }

    public String getOrderTopic() {
        return orderTopic;
    }

    public void setOrderTopic(String orderTopic) {
        this.orderTopic = defaultIfBlank(orderTopic, DEFAULT_ORDER_TOPIC);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
