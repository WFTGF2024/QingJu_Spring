package com.example.flashsale.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("kafka")
public class KafkaTopicConfig {
    @Bean
    NewTopic orderCreated(@Value("${app.kafka.topic}") String topic,
                          @Value("${app.kafka.partitions}") int p,
                          @Value("${app.kafka.replication}") int r) {
        return new NewTopic(topic, p, (short) r);
    }
}
