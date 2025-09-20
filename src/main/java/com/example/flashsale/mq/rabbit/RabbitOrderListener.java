package com.example.flashsale.mq.rabbit;

import com.example.flashsale.domain.OrderEvent;
import com.example.flashsale.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("rabbit")
@RequiredArgsConstructor
public class RabbitOrderListener {

    private final OrderService orderService;

    @RabbitListener(queues = "${app.mq.queue}")
    public void onMessage(OrderEvent evt, Channel channel, Message msg) throws Exception {
        long tag = msg.getMessageProperties().getDeliveryTag();
        try {
            orderService.persist(evt);
            channel.basicAck(tag, false);
        } catch (DataIntegrityViolationException e) {
            // 幂等：唯一索引冲突也 ack
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("[RABBIT-CONSUMER] error: {}", e.getMessage(), e);
            // 不可预期错误拒绝并丢入 DLQ
            channel.basicNack(tag, false, false);
        }
    }
}
