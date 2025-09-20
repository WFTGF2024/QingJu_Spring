package com.example.flashsale.mq.kafka;

import com.example.flashsale.domain.OrderEvent;
import com.example.flashsale.mq.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaOrderPublisher implements OrderEventPublisher {

    private final KafkaTemplate<String, OrderEvent> template;
    @Value("${app.kafka.topic}") String topic;

    @Override
    public void publish(OrderEvent evt) {
        String key = String.valueOf(evt.getVoucherId()); // 按券分区，保证局部有序
        template.send(topic, key, evt).whenComplete((res, ex) -> {
            if (ex != null) {
                log.error("[KAFKA-PUB] send failed: {}", ex.getMessage(), ex);
            } else {
                log.info("[KAFKA-PUB] sent {}", evt);
            }
        });
    }
}
