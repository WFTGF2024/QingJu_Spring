package com.example.flashsale.mq.rabbit;

import com.example.flashsale.domain.OrderEvent;
import com.example.flashsale.mq.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Date;

@Slf4j
@Component
@Profile("rabbit")
@RequiredArgsConstructor
public class RabbitMQOrderPublisher implements OrderEventPublisher {

    private final RabbitTemplate template;
    @Value("${app.mq.exchange}") String exchange;
    @Value("${app.mq.routingKey}") String rk;

    @PostConstruct
    void init() {
        template.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
            if (!ack) {
                log.error("[RABBIT-PUB] confirm failed: cd={}, cause={}", correlationData, cause);
            }
        });
        template.setReturnsCallback(returned -> {
            Message m = returned.getMessage();
            log.error("[RABBIT-PUB] returned: replyCode={}, replyText={}, exchange={}, routingKey={}, msgId={}",
                    returned.getReplyCode(), returned.getReplyText(),
                    returned.getExchange(), returned.getRoutingKey(),
                    m.getMessageProperties().getMessageId());
        });
    }

    @Override
    public void publish(OrderEvent evt) {
        CorrelationData cd = new CorrelationData(String.valueOf(evt.getOrderId()));
        template.convertAndSend(exchange, rk, evt, m -> {
            m.getMessageProperties().setMessageId(String.valueOf(evt.getOrderId()));
            m.getMessageProperties().setTimestamp(new Date(evt.getTs()));
            return m;
        }, cd);
        log.info("[RABBIT-PUB] sent {}", evt);
    }
}
