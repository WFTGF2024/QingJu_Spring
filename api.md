下面我先把 **接口文档** 和对应的 **PowerShell 版 curl 示例**都整理出来，最后给一大坨**面试常见提问点**，你可以按这个项目来准备。

---

# QingJu_Spring 接口文档

## 1. 统一说明

* 基础 URL：`http://localhost:8080`
* 返回统一格式（`ApiResponse<T>`）：

```json
{
  "code": 0,
  "msg": "ok",
  "data": { }
}
```

* `code` 含义：

    * `0`：成功
    * `1`：失败（配合 `msg` 看具体错误）
* 登录态：

    * 登录成功后返回一个 `token`（UUID），服务端在 Redis 中存 `login:token:{token}` 的 Hash。
    * 之后需要在请求头里带：`Authorization: Bearer <token>`。

---

## 2. 接口总览

| 模块       | 方法   | URL                        | 说明                 | 是否需要登录     |
| -------- | ---- | -------------------------- | ------------------ | ---------- |
| Auth     | POST | `/api/auth/code`           | 发送手机验证码            | 否          |
| Auth     | POST | `/api/auth/login`          | 手机号 + 验证码登录        | 否          |
| Auth     | GET  | `/api/auth/me`             | 获取当前登录用户信息         | 是          |
| Voucher  | POST | `/api/voucher/seckill`     | 创建秒杀券（预热库存到 Redis） | 否（当前实现不校验） |
| Seckill  | POST | `/api/seckill/{voucherId}` | 执行秒杀下单（Redis+MQ）   | 是          |
| Actuator | GET  | `/actuator/health`         | 健康检查               | 否          |

---

## 3. Auth 模块

### 3.1 发送验证码

**接口**

* Method：`POST`
* URL：`/api/auth/code`
* 描述：根据手机号生成 6 位随机验证码，写入 Redis（`login:code:{phone}`），TTL 2 分钟，并在开发环境直接返回验证码。

**请求参数**

* Query/Form 参数：

| 名称    | 类型     | 必填 | 说明                 |
| ----- | ------ | -- | ------------------ |
| phone | string | 是  | 手机号，正则 `^1\d{10}$` |

**成功响应示例**

```json
{
  "code": 0,
  "msg": "ok",
  "data": "123456"
}
```

**失败响应示例（手机号格式错误）**

```json
{
  "code": 1,
  "msg": "手机号格式错误",
  "data": null
}
```

**PowerShell curl 示例**

```powershell
# 发送验证码
curl.exe -X POST "http://localhost:8080/api/auth/code" `
  -H "Content-Type: application/x-www-form-urlencoded" `
  -d "phone=13800138000"
```

---

### 3.2 登录

**接口**

* Method：`POST`
* URL：`/api/auth/login`
* 描述：校验手机号 + 验证码，生成用户 ID，写入 Redis Hash 作为会话，返回 token。

**请求体**

Content-Type：`application/json`

```json
{
  "phone": "13800138000",
  "code": "123456"
}
```

* 对应 `LoginFormDTO`：

| 字段    | 类型     | 必填 | 说明  |
| ----- | ------ | -- | --- |
| phone | string | 是  | 手机号 |
| code  | string | 是  | 验证码 |

**成功响应示例**

```json
{
  "code": 0,
  "msg": "ok",
  "data": "550e8400-e29b-41d4-a716-446655440000"
}
```

这里 `data` 就是 token，服务端 Redis 里存的 key 为：

* `login:token:{token}`（Hash，字段：`id`、`phone`、`nickName`）

**失败响应示例（验证码错误或过期）**

```json
{
  "code": 1,
  "msg": "验证码错误或过期",
  "data": null
}
```

**PowerShell curl 示例**

```powershell
$body = @{
  phone = "13800138000"
  code  = "123456"
} | ConvertTo-Json

curl.exe -X POST "http://localhost:8080/api/auth/login" `
  -H "Content-Type: application/json" `
  -d $body
```

---

### 3.3 获取当前登录用户信息

**接口**

* Method：`GET`
* URL：`/api/auth/me`
* 描述：从 `Authorization: Bearer <token>` 中解析 token，从 Redis 取出用户信息并返回。

**请求头**

| 名称            | 必填 | 示例                              |
| ------------- | -- | ------------------------------- |
| Authorization | 是  | `Bearer 550e8400-e29b-41d4-...` |

**成功响应示例**

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "id": 1,
    "phone": "13800138000",
    "nickName": "u1"
  }
}
```

`data` 对应 `UserDTO`：

| 字段       | 类型     | 说明    |
| -------- | ------ | ----- |
| id       | long   | 用户 ID |
| phone    | string | 手机号   |
| nickName | string | 昵称    |

**失败响应示例**

* 未带 token 或格式不对：

```json
{
  "code": 1,
  "msg": "未登录",
  "data": null
}
```

* token 不存在或过期：

```json
{
  "code": 1,
  "msg": "登录已过期",
  "data": null
}
```

**PowerShell curl 示例**

```powershell
$token = "你的登录token"

curl.exe "http://localhost:8080/api/auth/me" `
  -H "Authorization: Bearer $token"
```

---

## 4. Voucher 模块

### 4.1 创建秒杀券（库存预热）

**接口**

* Method：`POST`
* URL：`/api/voucher/seckill`
* 描述：根据传入的 `stock` 创建一个秒杀券 ID（用当前时间戳），并把库存写入 Redis `seckill:stock:{voucherId}`。

**请求体**

Content-Type：`application/json`

```json
{
  "stock": 100
}
```

| 字段    | 类型  | 必填 | 说明   |
| ----- | --- | -- | ---- |
| stock | int | 是  | 初始库存 |

**成功响应示例**

```json
{
  "code": 0,
  "msg": "ok",
  "data": 1712141234567
}
```

`data` 即 `voucherId`（Long，时间戳）。

**PowerShell curl 示例**

```powershell
$body = @{
  stock = 100
} | ConvertTo-Json

curl.exe -X POST "http://localhost:8080/api/voucher/seckill" `
  -H "Content-Type: application/json" `
  -d $body
```

---

## 5. Seckill 模块

### 5.1 秒杀下单

**接口**

* Method：`POST`
* URL：`/api/seckill/{voucherId}`
* 描述：

    1. 校验登录（从 `Authorization` 取 token → Redis 取 userId）。
    2. 调用 Redis Lua 脚本：

        * `KEYS[1] = seckill:stock:{voucherId}`
        * `KEYS[2] = seckill:ordered:{voucherId}`
        * `ARGV[1] = userId`
        * 返回：

            * `0`：成功（减库存 + 记录用户）
            * `1`：库存不足
            * `2`：重复下单
    3. 使用 `RedisIdWorker` 生成全局唯一 `orderId`。
    4. 发送 `OrderEvent` 到 MQ（Rabbit/Kafka/Noop），由消费者异步落库。

**路径参数**

| 名称        | 类型   | 必填 | 说明     |
| --------- | ---- | -- | ------ |
| voucherId | long | 是  | 秒杀券 ID |

**请求头**

| 名称            | 必填 | 说明                     |
| ------------- | -- | ---------------------- |
| Authorization | 是  | `Bearer <登录返回的 token>` |

**成功响应示例**

```json
{
  "code": 0,
  "msg": "ok",
  "data": 9876543210123
}
```

`data` 即 `orderId`。注意：这个时候订单只是进入 MQ 队列，真正写入 MySQL 在消费者里异步完成。

**失败响应示例**

* 未登录：

```json
{
  "code": 1,
  "msg": "未登录",
  "data": null
}
```

* 登录过期：

```json
{
  "code": 1,
  "msg": "登录已过期",
  "data": null
}
```

* Lua 返回库存不足：

```json
{
  "code": 1,
  "msg": "库存不足",
  "data": null
}
```

* Lua 返回重复下单：

```json
{
  "code": 1,
  "msg": "请勿重复下单",
  "data": null
}
```

**PowerShell curl 示例**

```powershell
$token = "你的登录token"
$voucherId = 1712141234567

curl.exe -X POST "http://localhost:8080/api/seckill/$voucherId" `
  -H "Authorization: Bearer $token"
```

（该接口不需要请求体）

---

## 6. Actuator 健康检查

**接口**

* Method：`GET`
* URL：`/actuator/health`
* 描述：健康检查，默认 Actuator 开了这个端点。

**成功响应示例**

典型返回（默认情况下）：

```json
{
  "status": "UP"
}
```

**PowerShell curl 示例**

```powershell
curl.exe "http://localhost:8080/actuator/health"
```

---

# 7. 面试中可能被问到什么？

这个项目本质上是一个「**高并发秒杀 + 登录 + Redis + MQ + JPA**」的小型综合题，面试官脑子里的菜单大概是这样的：

---

## 7.1 接口 & 业务设计相关

* 你整个接口调用链路是怎样的？从用户点击 “秒杀” 到订单落库，中间有哪些关键步骤？
* 为什么登录要把 token 存 Redis Hash，而不是 JWT 放在客户端自己校验？
* `ApiResponse<T>` 这种统一返回结构有什么好处？在真实项目中你会不会拆出错误码枚举？

---

## 7.2 高并发秒杀 & Redis / Lua

* 秒杀场景里为什么要用 **Redis + Lua** 来扣减库存，而不是直接 MySQL `UPDATE ... WHERE stock > 0`？

    * 要点：单线程、原子性、性能、避免大量 DB 写。
* 你 Lua 脚本具体做了什么？三个返回值分别代表什么含义？
  如何避免超卖 & 重复下单？
* `seckill:ordered:{voucherId}` 使用 `Set` 保存 userId，有什么优势和缺点？

    * 优点：天然去重；
    * 缺点：内存占用随参与人数线性增长。
* 如果流量再大一个量级，会怎么做限流/削峰？（Nginx 限流、令牌桶、本地缓存兜底等）

---

## 7.3 消息队列 & 一致性

* 为什么要用 MQ 做 **异步下单**，而不是在接口里直接 `orderRepository.save()`？

    * 要点：削峰、解耦、避免长事务、用户快速返回。
* 在 RabbitMQ/Kafka 实现里，你是怎么保证 **消息可靠性 & 幂等性** 的？

    * 生产端：确认机制 / 重试；
    * 消费端：手动 ack、异常时 nack 或抛出重试；
    * 业务端：数据库唯一约束 + try/catch `DataIntegrityViolationException` 当成幂等成功。
* 如果消息成功写入 MQ，但消费者落库失败怎么办？会不会丢单？
  你是如何处理死信队列（DLQ）或重试的？

---

## 7.4 数据库设计 & 事务

* `OrderEntity` 上为什么要加唯一约束 `uk_user_voucher(user_id, voucher_id)`？

    * 要点：防止同一用户同一券多单，配合 MQ 消费端的幂等。
* `OrderServiceImpl.persist` 上的 `@Transactional` 起什么作用？为什么要在这里加，而不是 MQ 监听器那里？
* 如果数据库写入失败，但库存已经在 Redis 中扣减了，怎么保证业务上的最终一致性？
  你会不会设计一个补偿任务或对账任务？

---

## 7.5 Redis ID 生成器

* `RedisIdWorker` 的 ID 结构是什么？为什么用 `(timestamp << 32) | count` 这种做法？

    * 时间 + 自增序列，保证趋势递增且全局唯一。
* 跟雪花算法（Snowflake）相比，这种方案有什么优缺点？

    * 集中依赖 Redis vs. 去中心化；
    * 实现简单 vs. 时钟漂移问题。

---

## 7.6 配置 & Profile 管理

* 你项目里有 `rabbit`、`kafka`、默认（noop）三种实现，为什么要用 Spring Profile？
* 如果线上要从 Rabbit 切换到 Kafka，要改哪些地方？能否做到不用改业务代码？
* `application.yml` 和 `application-kafka.yml` / `application-rabbit.yml` 是怎么配合的？

---

## 7.7 监控 &运维

* Actuator 你现在只开放了 `/actuator/health`，如果是生产环境，你还会开放哪些端点？
  （`/metrics`、`/loggers`、`/prometheus` 等）
* 生产里如何监控秒杀系统？你会关注哪些指标？

    * QPS、错误率、库存扣减成功率、MQ 堆积长度、DB 写入耗时等。

---

## 7.8 扩展 &演进方向（比较“前瞻”的问法）

* 如果活动从单券秒杀扩展到 **多场活动、预热+开始+结束时间**，你会怎样调整：

    * Redis key 设计？
    * Lua 脚本入参？
    * 数据表结构（活动表、券表、订单表）？
* 现在是单机 Redis，如果要做 Redis Cluster，秒杀逻辑如何保证 key 一致路由？
  会不会受 Lua 脚本执行节点的影响？
* 如何把这个 Demo 演进成支持水平扩展的微服务架构？
  例如拆成鉴权服务、库存服务、订单服务等。

---

如果你能把以上接口细节 + Redis/Lua + MQ/幂等 + ID 生成器这些点讲清楚，再顺便画一下调用时序图，面试官大概率会觉得你不仅能“写代码跑起来”，还对系统在高并发场景下的行为有全局认知。
