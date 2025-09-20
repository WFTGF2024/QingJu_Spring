package com.example.flashsale.service.impl;

import com.example.flashsale.domain.OrderEvent;
import com.example.flashsale.entity.OrderEntity;
import com.example.flashsale.repository.OrderRepository;
import com.example.flashsale.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public void persist(OrderEvent evt) {
        OrderEntity e = new OrderEntity();
        e.setId(evt.getOrderId());
        e.setUserId(evt.getUserId());
        e.setVoucherId(evt.getVoucherId());
        try {
            orderRepository.save(e);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // JPA wraps unique constraint as DataIntegrityViolationException
            // Treat as idempotent success
            if (ex.getMessage() != null && ex.getMessage().contains("uk_user_voucher")) {
                return;
            }
            throw ex;
        }
    }
}
