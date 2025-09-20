package com.example.flashsale.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class RedisIdWorker {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy:MM:dd");
    private final StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long ts = now.toEpochSecond(ZoneOffset.UTC);
        String key = "icr:" + keyPrefix + ":" + now.format(FMT);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        return (ts << 32) | (count != null ? count : 0L);
    }
}
