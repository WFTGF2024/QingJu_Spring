package com.example.flashsale.mq;

import com.example.flashsale.domain.OrderEvent;

public interface OrderEventPublisher {
    void publish(OrderEvent evt);
}
