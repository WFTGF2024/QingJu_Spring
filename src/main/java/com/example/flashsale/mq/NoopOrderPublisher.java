package com.example.flashsale.mq;

import com.example.flashsale.domain.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!rabbit & !kafka")
public class NoopOrderPublisher implements OrderEventPublisher {
    @Override
    public void publish(OrderEvent evt) {
        log.info("[NOOP-PUB] Received order event: {}", evt);
    }
}
