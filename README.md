# QingJu_Spring

基于 **Spring Boot 3.3** + **Redis** + **MySQL** + **RabbitMQ/Kafka** 的高并发秒杀系统示例。

## 项目简介

本项目是一个高并发秒杀系统的示例实现，采用前后端分离架构，后端基于 Spring Boot 3.3.2 和 Java 21 构建。系统核心功能包括用户认证、秒杀券管理和秒杀下单，通过 Redis Lua 脚本保证库存扣减的原子性，利用消息队列实现异步下单，有效应对高并发场景。

### 技术栈

| 技术 | 版本 | 说明 |
|-----|------|-----|
| Spring Boot | 3.3.2 | 核心框架 |
| Java | 21 | 开发语言 |
| Spring Data JPA | 3.3.2 | ORM框架 |
| Spring Data Redis | 3.3.2 | 缓存中间件 |
| MySQL | - | 关系型数据库 |
| RabbitMQ / Kafka | - | 消息队列 |
| Lombok | - | 代码简化工具 |

### 核心特性

- **手机号 + 验证码登录**：验证码和 token 存储在 Redis，支持自动过期
- **秒杀券创建**：库存预热到 Redis，支持高并发访问
- **秒杀下单**：Redis Lua 原子扣减库存 + RabbitMQ/Kafka 异步下单落库
- **分布式ID生成**：基于 Redis 的全局唯一ID生成器
- **防重复下单**：Redis Set + 数据库唯一约束双重保障
- **可插拔MQ架构**：支持 RabbitMQ、Kafka 和 Noop 三种模式
- **Actuator 健康检查**：提供应用健康状态监控

### 项目结构

```
src/main/java/com/example/flashsale/
├── config/          # 配置类
├── controller/      # 控制器层
│   ├── AuthController.java      # 认证控制器
│   ├── SeckillController.java   # 秒杀控制器
│   └── VoucherController.java   # 优惠券控制器
├── entity/          # 实体类
├── domain/          # 领域对象/DTO
├── mq/              # 消息队列
│   ├── kafka/       # Kafka 实现
│   └── rabbit/      # RabbitMQ 实现
├── redis/           # Redis 相关组件
├── repository/      # 数据访问层
└── service/         # 服务层
```

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

## 核心设计

### 秒杀流程

```
用户秒杀流程:
1. POST /api/auth/code → 获取验证码
2. POST /api/auth/login → 登录获取Token
3. POST /api/voucher/seckill → 创建秒杀券(管理员)
4. POST /api/seckill/{voucherId} → 秒杀下单
   ├── Redis Lua脚本原子扣库存
   ├── 生成全局唯一订单ID
   └── 发送MQ消息异步落库
5. MQ消费者 → 订单持久化到MySQL
```

---

## 后端处理流程

### 1. 用户认证流程

#### 1.1 发送验证码流程

```
用户请求发送验证码
    ↓
AuthController.code()
    ↓
校验手机号格式 (正则: ^1\d{10}$)
    ↓
生成6位随机验证码
    ↓
存入Redis: login:code:{phone} = {code}
    ↓
设置过期时间: 2分钟
    ↓
返回验证码 (开发环境直接返回，生产环境通过短信发送)
```

**关键代码位置**: `AuthController.java:code()`

#### 1.2 登录流程

```
用户提交手机号+验证码
    ↓
AuthController.login()
    ↓
从Redis获取验证码: login:code:{phone}
    ↓
校验验证码是否正确
    ↓
生成用户ID (Redis自增: icr:user)
    ↓
生成Token (UUID)
    ↓
存储用户会话到Redis:
    Key: login:token:{token}
    Type: Hash
    Fields: {id, phone, nickName}
    TTL: 30分钟
    ↓
删除验证码
    ↓
返回Token
```

**关键代码位置**: `AuthController.java:login()`

**Redis数据结构**:
```
login:token:{token} = {
    id: 1,
    phone: "13800138000",
    nickName: "用户13800138000"
}
```

#### 1.3 获取用户信息流程

```
请求携带Token (Header: Authorization: Bearer {token})
    ↓
AuthController.me()
    ↓
从Header解析Token
    ↓
从Redis获取用户信息: login:token:{token}
    ↓
返回UserDTO
```

**关键代码位置**: `AuthController.java:me()`

---

### 2. 秒杀券创建流程

```
管理员创建秒杀券 (stock: 100)
    ↓
VoucherController.createSeckillVoucher()
    ↓
生成voucherId (当前时间戳)
    ↓
库存预热到Redis:
    Key: seckill:stock:{voucherId}
    Value: {stock}
    ↓
返回voucherId
```

**关键代码位置**: `VoucherController.java:createSeckillVoucher()`

**Redis数据结构**:
```
seckill:stock:1704067200000 = "100"
```

---

### 3. 秒杀下单核心流程

#### 3.1 整体流程

```
用户请求秒杀下单
    ↓
SeckillController.seckill()
    ↓
[步骤1] 校验登录状态
    ├── 从Header获取Token
    ├── 从Redis获取userId: login:token:{token}
    └── 失败则返回"未登录"或"登录已过期"
    ↓
[步骤2] 执行Redis Lua脚本 (原子操作)
    ├── 检查是否重复下单
    ├── 检查库存是否充足
    ├── 扣减库存
    └── 记录已下单用户
    ↓
[步骤3] 判断Lua脚本返回值
    ├── 返回1 → 库存不足
    ├── 返回2 → 重复下单
    └── 返回0 → 继续
    ↓
[步骤4] 生成全局唯一订单ID
    └── RedisIdWorker.nextId()
    ↓
[步骤5] 构建订单消息
    └── {orderId, userId, voucherId}
    ↓
[步骤6] 发送消息到MQ
    ├── RabbitMQ: exchange=order.events, routingKey=order.created
    └── Kafka: topic=order.created
    ↓
[步骤7] 返回订单ID给用户
```

**关键代码位置**: `SeckillController.java:seckill()`

#### 3.2 Redis Lua脚本详解

```lua
-- 脚本位置: src/main/resources/scripts/seckill.lua

-- KEYS[1]: seckill:stock:{voucherId}  库存key
-- KEYS[2]: seckill:ordered:{voucherId}  已下单用户集合
-- ARGV[1]: userId

-- 1. 检查用户是否已下单
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return 2  -- 重复下单
end

-- 2. 检查库存
local stock = redis.call('get', KEYS[1])
if not stock or tonumber(stock) <= 0 then
    return 1  -- 库存不足
end

-- 3. 扣减库存
redis.call('decr', KEYS[1])

-- 4. 记录已下单用户
redis.call('sadd', KEYS[2], ARGV[1])

return 0  -- 成功
```

**为什么使用Lua脚本**:
- **原子性**: 整个操作在Redis中原子执行，避免并发问题
- **减少网络开销**: 多个Redis命令一次发送
- **防止超卖**: 库存检查和扣减在同一事务中

**Redis数据结构变化**:
```
执行前:
seckill:stock:1704067200000 = "100"
seckill:ordered:1704067200000 = {}

执行后 (userId=1 下单成功):
seckill:stock:1704067200000 = "99"
seckill:ordered:1704067200000 = {1}
```

#### 3.3 分布式ID生成器

```
RedisIdWorker.nextId()
    ↓
获取当前时间戳 (秒级)
    ↓
Redis自增: icr:order
    ↓
ID = (timestamp << 32) | count
    ↓
返回全局唯一ID
```

**ID结构**:
```
|---- 时间戳 (32位) ----|---- 序列号 (32位) ----|
```

**特点**:
- 趋势递增: 时间戳在高位
- 全局唯一: Redis自增保证
- 高性能: 单Redis节点可支持百万级QPS

**关键代码位置**: `RedisIdWorker.java:nextId()`

---

### 4. 消息队列异步处理流程

#### 4.1 RabbitMQ模式

```
[生产者] SeckillController
    ↓
RabbitMQOrderPublisher.publish()
    ↓
发送到Exchange: order.events
RoutingKey: order.created
    ↓
[队列] order.created.q
    ↓
[消费者] RabbitOrderListener
    ↓
手动ACK模式
    ↓
OrderService.createOrder()
    ↓
[步骤1] 创建订单实体
    OrderEntity {
        id: orderId,
        userId: userId,
        voucherId: voucherId,
        status: 1 (CREATED),
        createdAt: timestamp
    }
    ↓
[步骤2] 保存到MySQL
    INSERT INTO t_order ...
    ↓
[步骤3] ACK确认
    ↓
处理完成
```

**关键代码位置**:
- 生产者: `RabbitMQOrderPublisher.java`
- 消费者: `RabbitOrderListener.java`
- 服务: `OrderService.java`

**RabbitMQ配置**:
```yaml
Exchange: order.events (Direct)
Queue: order.created.q
RoutingKey: order.created
Dead Letter Exchange: order.dlx
Dead Letter Queue: order.created.dlq
Prefetch: 50
Concurrency: 2-8
```

#### 4.2 Kafka模式

```
[生产者] SeckillController
    ↓
KafkaOrderPublisher.publish()
    ↓
发送到Topic: order.created
Partition策略: orderId.hashCode() % 6
    ↓
[分区] order.created (6个分区)
    ↓
[消费者] KafkaOrderListener
Consumer Group: order-consumer-g1
    ↓
手动ACK模式 (MANUAL_IMMEDIATE)
    ↓
OrderService.createOrder()
    ↓
保存订单到MySQL
    ↓
ACK确认 (ack.acknowledge())
```

**关键代码位置**:
- 生产者: `KafkaOrderPublisher.java`
- 消费者: `KafkaOrderListener.java`

**Kafka配置**:
```yaml
Topic: order.created
Partitions: 6
Consumer Group: order-consumer-g1
Ack Mode: MANUAL_IMMEDIATE
```

#### 4.3 异常处理与幂等性

```
消费者处理订单
    ↓
try {
    保存订单到MySQL
    ↓
    成功 → ACK确认
} catch (DuplicateKeyException) {
    ↓
    唯一约束冲突 (uk_user_voucher)
    ↓
    说明订单已存在 (幂等性保证)
    ↓
    ACK确认 (不重复处理)
} catch (Exception e) {
    ↓
    其他异常
    ↓
    NACK/重试 或 进入死信队列
}
```

**幂等性保障机制**:
1. **数据库唯一约束**: `uk_user_voucher(user_id, voucher_id)`
2. **Redis Set**: `seckill:ordered:{voucherId}` 记录已下单用户
3. **消息ACK机制**: 只有处理成功才确认

---

### 5. 数据流转全景图

```
┌─────────────┐
│   用户请求   │
└──────┬──────┘
       ↓
┌──────────────────────────────────────┐
│         Spring Boot 应用              │
│  ┌────────────────────────────────┐  │
│  │      Controller层              │  │
│  │  - 参数校验                    │  │
│  │  - 业务逻辑调用                │  │
│  └────────────┬───────────────────┘  │
│               ↓                       │
│  ┌────────────────────────────────┐  │
│  │      Service层                 │  │
│  │  - 业务编排                    │  │
│  │  - 事务管理                    │  │
│  └────────────┬───────────────────┘  │
└───────────────┼──────────────────────┘
                ↓
    ┌───────────┴───────────┐
    ↓                       ↓
┌─────────┐           ┌──────────┐
│  Redis  │           │    MQ    │
│ ─────── │           │ ──────── │
│ 验证码  │           │ RabbitMQ │
│ Token   │           │  Kafka   │
│ 库存    │           └────┬─────┘
│ 用户集合│                │
│ ID生成  │                │
└─────────┘                ↓
                     ┌──────────────┐
                     │  MQ消费者    │
                     │  ──────────  │
                     │  订单落库    │
                     └──────┬───────┘
                            ↓
                     ┌──────────────┐
                     │    MySQL     │
                     │   ────────   │
                     │  t_order表   │
                     └──────────────┘
```

---

### 6. 并发场景处理

#### 6.1 防止超卖

```
场景: 100个用户同时抢购最后1件商品

传统方案 (不安全):
┌─────────┐
│ 用户A   │ → 查库存=1 → 扣减 → 保存
└─────────┘
┌─────────┐
│ 用户B   │ → 查库存=1 → 扣减 → 保存  ← 超卖!
└─────────┘

Lua脚本方案 (安全):
┌─────────┐
│ 用户A   │ → Lua脚本执行 → 库存=0 → 成功
└─────────┘
┌─────────┐
│ 用户B   │ → Lua脚本执行 → 库存=0 → 失败
└─────────┘
```

#### 6.2 防止重复下单

```
双重保障机制:

第一层: Redis Set
┌─────────────────────────────────┐
│ seckill:ordered:{voucherId}     │
│ ─────────────────────────────── │
│ SISMEMBER检查 → SADD记录        │
│ (Lua脚本中原子执行)             │
└─────────────────────────────────┘

第二层: 数据库唯一约束
┌─────────────────────────────────┐
│ uk_user_voucher(user_id,        │
│                 voucher_id)     │
│ ─────────────────────────────── │
│ INSERT时自动检查                │
│ 冲突则抛出DuplicateKeyException │
└─────────────────────────────────┘
```

---

### Redis Lua 脚本

秒杀核心逻辑通过 Lua 脚本实现原子性操作：

- **KEYS[1]**: `seckill:stock:{voucherId}` - 库存key
- **KEYS[2]**: `seckill:ordered:{voucherId}` - 已下单用户集合
- **ARGV[1]**: userId
- **返回值**: 0=成功, 1=库存不足, 2=重复下单

### 分布式ID生成器

基于 Redis 实现的全局唯一ID生成器，ID结构：`(timestamp << 32) | count`

特点：
- 时间戳 + 自增序列，保证趋势递增
- 全局唯一性
- 高性能

### 消息队列架构

项目支持三种MQ模式（通过Spring Profile切换）：

| 模式 | Profile | 说明 |
|-----|---------|-----|
| RabbitMQ | `rabbit` | 推荐，功能完善 |
| Kafka | `kafka` | 高吞吐场景 |
| Noop | 默认 | 仅日志输出，用于测试 |

---

## 注意事项

- MySQL 需要在启动时加 `allowPublicKeyRetrieval=true`，已在 `application.yml` 中配置
- 店铺查询相关接口在这个精简版中未实现
- Actuator 默认只开放 `/health`，如需更多端点可在配置中开启：
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: health,info
  ```

---

## 相关文档

- [API接口文档](./API.md) - 详细的接口说明和示例

---

## License

MIT License

---
