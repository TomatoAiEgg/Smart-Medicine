package com.zhyf.workflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkflowPropertiesTest {

    @Test
    void shouldUseDefaultsWhenBlankValuesAreBound() {
        WorkflowProperties properties = new WorkflowProperties();

        properties.setOrderServiceBaseUrl("");
        properties.getRocketmq().setNameServer(" ");
        properties.getRocketmq().setConsumerGroup(null);
        properties.getRocketmq().setOrderTopic("");

        assertThat(properties.getOrderServiceBaseUrl()).isEqualTo("http://localhost:18082");
        assertThat(properties.getRocketmq().getNameServer()).isEqualTo("100.115.165.51:9876");
        assertThat(properties.getRocketmq().getConsumerGroup()).isEqualTo("zhyf-workflow-order-consumer");
        assertThat(properties.getRocketmq().getOrderTopic()).isEqualTo("zhyf-order-event");
    }
}
