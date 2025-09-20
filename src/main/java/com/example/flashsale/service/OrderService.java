package com.example.flashsale.service;

import com.example.flashsale.domain.OrderEvent;

public interface OrderService {
    void persist(OrderEvent evt);
}
