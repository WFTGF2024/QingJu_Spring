# QingJu_Spring

基于 **Spring Boot 3.3** + **Redis** + **MySQL** + **RabbitMQ/Kafka** 的高并发秒杀系统示例。  

主要功能：  
- 手机号 + 验证码登录（验证码和 token 存 Redis）  
- 秒杀券创建（库存预热到 Redis）  
- 秒杀下单（Redis Lua 原子扣减库存 + RabbitMQ/Kafka 异步下单落库）  
- Actuator 健康检查  

---

## 环境准备

### 1. 启动依赖服务
在项目根目录运行：
```bash
docker compose up -d
```
会启动：
- MySQL (localhost:13306, 用户 root/root, 库 flashsale)
- Redis (localhost:16379)
- RabbitMQ (localhost:5672, 管理界面 http://localhost:15672, 用户 guest/guest)
- Redpanda/Kafka (localhost:9092)

### 2. 启动应用
进入项目目录：
```bash
# RabbitMQ 模式
./gradlew bootRun --args='--spring.profiles.active=rabbit'

# Kafka 模式
./gradlew bootRun --args='--spring.profiles.active=kafka'
```

应用默认监听 `http://localhost:8080`。

---

## CMD 测试命令

### 设置基础环境变量
```cmd
set BASE=http://localhost:8080
```

### 1. 获取验证码
```cmd
curl -X POST "%BASE%/api/auth/code" -H "Content-Type: application/x-www-form-urlencoded" -d "phone=13800138000"
```

### 2. 登录获取 Token
```cmd
curl -X POST "%BASE%/api/auth/login" -H "Content-Type: application/json" -d "{\"phone\":\"13800138000\",\"code\":\"<上一步返回的验证码>\"}"
```
返回的 `data` 是 token，设置到环境变量：
```cmd
set TOKEN=<粘贴返回的 token>
```

### 3. 获取当前用户信息
```cmd
curl "%BASE%/api/auth/me" -H "Authorization: Bearer %TOKEN%"
```

### 4. 发布秒杀券
```cmd
curl -X POST "%BASE%/api/voucher/seckill" -H "Content-Type: application/json" -d "{\"stock\": 100}"
```
返回的 `data` 是 `voucherId`。

### 5. 秒杀下单
```cmd
curl -X POST "%BASE%/api/seckill/<voucherId>" -H "Authorization: Bearer %TOKEN%"
```
将 `<voucherId>` 替换为上一步返回的实际 ID。

### 6. 健康检查
```cmd
curl "%BASE%/actuator/health"
curl "%BASE%/actuator/health/redis"
```

---

## 验证 RabbitMQ
- 浏览器访问 [http://localhost:15672](http://localhost:15672)，登录 guest/guest。  
- 在 **Queues** 页面可以看到 `order.created.q`，下单成功时消息会进入队列并被消费。  

---

## 注意事项
- MySQL 需要在启动时加 `allowPublicKeyRetrieval=true`，已在 `application.yml` 中配置。  
- 店铺查询相关接口在这个精简版中未实现。  
- Actuator 默认只开放 `/health`，如需更多端点可在配置中开启：  
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: health,info
  ```

---
