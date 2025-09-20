package com.example.flashsale.controller;

import com.example.flashsale.domain.ApiResponse;
import com.example.flashsale.domain.LoginFormDTO;
import com.example.flashsale.domain.UserDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final StringRedisTemplate redis;

    @PostMapping("/code")
    public ApiResponse<String> code(@RequestParam String phone) {
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            return ApiResponse.fail("手机号格式错误");
        }
        String code = String.format("%06d", new Random().nextInt(1000000));
        redis.opsForValue().set("login:code:" + phone, code, 2, TimeUnit.MINUTES);
        // 开发环境直接回显
        return ApiResponse.ok(code);
    }

    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody LoginFormDTO form) {
        if (form.getPhone() == null || !form.getPhone().matches("^1\\d{10}$")) {
            return ApiResponse.fail("手机号错误");
        }
        String cache = redis.opsForValue().get("login:code:" + form.getPhone());
        if (!StringUtils.hasText(cache) || !cache.equals(form.getCode())) {
            return ApiResponse.fail("验证码错误或过期");
        }
        // 模拟创建用户：这里用自增ID
        Long uid = redis.opsForValue().increment("icr:user") ;
        UserDTO user = new UserDTO(uid, form.getPhone(), "u" + uid);
        String token = UUID.randomUUID().toString();
        String tokenKey = "login:token:" + token;
        redis.opsForHash().put(tokenKey, "id", String.valueOf(user.getId()));
        redis.opsForHash().put(tokenKey, "phone", user.getPhone());
        redis.opsForHash().put(tokenKey, "nickName", user.getNickName());
        redis.expire(tokenKey, 30, TimeUnit.MINUTES);
        return ApiResponse.ok(token);
    }

    @GetMapping("/me")
    public ApiResponse<UserDTO> me(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return ApiResponse.fail("未登录");
        }
        String token = auth.substring(7);
        var m = redis.opsForHash().entries("login:token:" + token);
        if (m == null || m.isEmpty()) return ApiResponse.fail("登录已过期");
        Long id = Long.valueOf((String) m.get("id"));
        String phone = (String) m.get("phone");
        String nick = (String) m.get("nickName");
        return ApiResponse.ok(new UserDTO(id, phone, nick));
    }
}
