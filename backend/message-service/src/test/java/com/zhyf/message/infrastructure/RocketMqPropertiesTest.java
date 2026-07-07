package com.zhyf.message.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RocketMqPropertiesTest {

    @Test
    void shouldUseDefaultsWhenBlankValuesAreBound() {
        RocketMqProperties properties = new RocketMqProperties();

        properties.setNameServer("");
        properties.setProducerGroup(" ");
        properties.setConsumerGroup(null);
        properties.setOrderTopic("");

        assertThat(properties.getNameServer()).isEqualTo("100.115.165.51:9876");
        assertThat(properties.getProducerGroup()).isEqualTo("zhyf-message-producer");
        assertThat(properties.getConsumerGroup()).isEqualTo("zhyf-message-order-consumer");
        assertThat(properties.getOrderTopic()).isEqualTo("zhyf-order-event");
    }
}
