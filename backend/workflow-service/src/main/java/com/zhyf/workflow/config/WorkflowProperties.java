package com.zhyf.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "zhyf.workflow")
public class WorkflowProperties {

    private static final String DEFAULT_ORDER_SERVICE_BASE_URL = "http://localhost:18082";

    private String orderServiceBaseUrl = DEFAULT_ORDER_SERVICE_BASE_URL;
    private final RocketMq rocketmq = new RocketMq();

    public String getOrderServiceBaseUrl() {
        return orderServiceBaseUrl;
    }

    public void setOrderServiceBaseUrl(String orderServiceBaseUrl) {
        this.orderServiceBaseUrl = defaultIfBlank(orderServiceBaseUrl, DEFAULT_ORDER_SERVICE_BASE_URL);
    }

    public RocketMq getRocketmq() {
        return rocketmq;
    }

    public static class RocketMq {
        private static final String DEFAULT_NAME_SERVER = "100.115.165.51:9876";
        private static final String DEFAULT_CONSUMER_GROUP = "zhyf-workflow-order-consumer";
        private static final String DEFAULT_ORDER_TOPIC = "zhyf-order-event";

        private String nameServer = DEFAULT_NAME_SERVER;
        private String consumerGroup = DEFAULT_CONSUMER_GROUP;
        private String orderTopic = DEFAULT_ORDER_TOPIC;

        public String getNameServer() {
            return nameServer;
        }

        public void setNameServer(String nameServer) {
            this.nameServer = defaultIfBlank(nameServer, DEFAULT_NAME_SERVER);
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
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
