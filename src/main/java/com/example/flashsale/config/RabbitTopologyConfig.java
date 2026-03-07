package com.example.flashsale.config;

import com.example.flashsale.domain.OrderEvent;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("rabbit")
public class RabbitTopologyConfig {

    @Value("${app.mq.exchange}") String exchange;
    @Value("${app.mq.queue}") String queue;
    @Value("${app.mq.routingKey}") String rk;
    @Value("${app.mq.dlx}") String dlx;
    @Value("${app.mq.dlq}") String dlq;

    @Bean TopicExchange orderExchange() { return new TopicExchange(exchange, true, false); }
    @Bean TopicExchange dlxExchange() { return new TopicExchange(dlx, true, false); }

    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", dlx)
                .withArgument("x-dead-letter-routing-key", "order.created.dead")
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(dlq).build();
    }

    @Bean Binding bindOrder() { return BindingBuilder.bind(orderQueue()).to(orderExchange()).with(rk); }
    @Bean Binding bindDLQ() { return BindingBuilder.bind(deadLetterQueue()).to(dlxExchange()).with("order.created.dead"); }

    // 使用 JSON 消息转换器，避免 Java 序列化的安全问题
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
