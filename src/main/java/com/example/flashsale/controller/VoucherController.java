package com.example.flashsale.controller;

import com.example.flashsale.domain.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/voucher")
@RequiredArgsConstructor
public class VoucherController {

    private final StringRedisTemplate redis;

    @PostMapping("/seckill")
    public ApiResponse<Long> createSeckill(@RequestBody CreateVoucherReq req) {
        long vid = System.currentTimeMillis(); // 简化：用时间戳作为券ID
        String stockKey = "seckill:stock:" + vid;
        redis.opsForValue().set(stockKey, String.valueOf(req.getStock()));
        return ApiResponse.ok(vid);
    }

    @Data
    public static class CreateVoucherReq {
        private int stock;
    }
}
