package com.example.flashsale.mq.kafka;

import com.example.flashsale.domain.OrderEvent;
import com.example.flashsale.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaOrderListener {

    private final OrderService orderService;

    @KafkaListener(id = "order-consumer", topics = "${app.kafka.topic}", concurrency = "6")
    public void onMessage(ConsumerRecord<String, OrderEvent> rec, Acknowledgment ack) {
        try {
            orderService.persist(rec.value());
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[KAFKA-CONSUMER] error: {}", e.getMessage(), e);
            // 抛出异常将触发容器退避重试；也可集成 RetryTopic + DLT
            throw e;
        }
    }
}
