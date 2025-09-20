package com.example.flashsale.controller;

import com.example.flashsale.domain.ApiResponse;
import com.example.flashsale.domain.OrderEvent;
import com.example.flashsale.mq.OrderEventPublisher;
import com.example.flashsale.redis.RedisIdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> seckillScript;
    private final RedisIdWorker idWorker;
    private final OrderEventPublisher publisher;

    @PostMapping("/{voucherId}")
    public ApiResponse<Long> seckill(@PathVariable("voucherId") Long voucherId,
                                     @RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return ApiResponse.fail("未登录");
        }
        String token = auth.substring(7);
        var m = redis.opsForHash().entries("login:token:" + token);
        if (m == null || m.isEmpty()) return ApiResponse.fail("登录已过期");
        Long userId = Long.valueOf((String) m.get("id"));

        String stockKey = "seckill:stock:" + voucherId;
        String userSetKey = "seckill:ordered:" + voucherId;

        Long r = redis.execute(seckillScript, List.of(stockKey, userSetKey), userId.toString());
        if (r == null) return ApiResponse.fail("系统繁忙");
        if (r == 1L) return ApiResponse.fail("库存不足");
        if (r == 2L) return ApiResponse.fail("请勿重复下单");

        long orderId = idWorker.nextId("order");
        OrderEvent evt = OrderEvent.builder()
                .orderId(orderId)
                .userId(userId)
                .voucherId(voucherId)
                .ts(System.currentTimeMillis())
                .build();

        // 发布到可插拔 MQ
        publisher.publish(evt);
        return ApiResponse.ok(orderId);
    }
}
