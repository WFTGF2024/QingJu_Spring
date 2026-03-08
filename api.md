# QingJu_Spring API 接口文档

## 概述

本文档描述了 QingJu_Spring 秒杀系统的所有 API 接口。

- **Base URL**: `http://localhost:8080`
- **数据格式**: JSON
- **字符编码**: UTF-8

---

## 目录

- [1. 认证模块 (Auth)](#1-认证模块-auth)
  - [1.1 发送验证码](#11-发送验证码)
  - [1.2 登录](#12-登录)
  - [1.3 获取当前用户信息](#13-获取当前用户信息)
- [2. 优惠券模块 (Voucher)](#2-优惠券模块-voucher)
  - [2.1 创建秒杀券](#21-创建秒杀券)
- [3. 秒杀模块 (Seckill)](#3-秒杀模块-seckill)
  - [3.1 秒杀下单](#31-秒杀下单)
- [4. 健康检查模块 (Actuator)](#4-健康检查模块-actuator)
  - [4.1 应用健康检查](#41-应用健康检查)
  - [4.2 Redis健康检查](#42-redis健康检查)
- [5. 数据模型](#5-数据模型)
- [6. 错误码说明](#6-错误码说明)

---

## 1. 认证模块 (Auth)

### 1.1 发送验证码

**接口描述**: 向指定手机号发送6位数字验证码，验证码有效期2分钟。

**请求信息**:
- **URL**: `/api/auth/code`
- **Method**: `POST`
- **Content-Type**: `application/x-www-form-urlencoded`

**请求参数**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|------|------|------|
| phone | String | 是 | 手机号，需符合正则 `^1\d{10}$` |

**请求示例**:

```bash
curl -X POST "http://localhost:8080/api/auth/code" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "phone=13800138000"
```

**响应参数**:

| 参数名 | 类型 | 说明 |
|-------|------|------|
| success | Boolean | 是否成功 |
| data | String | 验证码（开发环境返回，生产环境为null） |
| errorMsg | String | 错误信息 |

**响应示例**:

```json
{
  "success": true,
  "data": "123456",
  "errorMsg": null
}
```

**错误响应**:

```json
{
  "success": false,
  "data": null,
  "errorMsg": "手机号格式不正确"
}
```

---

### 1.2 登录

**接口描述**: 使用手机号和验证码登录，成功返回Token。

**请求信息**:
- **URL**: `/api/auth/login`
- **Method**: `POST`
- **Content-Type**: `application/json`

**请求参数**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|------|------|------|
| phone | String | 是 | 手机号 |
| code | String | 是 | 验证码（6位数字） |

**请求示例**:

```bash
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "13800138000",
    "code": "123456"
  }'
```

**响应参数**:

| 参数名 | 类型 | 说明 |
|-------|------|------|
| success | Boolean | 是否成功 |
| data | String | Token字符串，有效期30分钟 |
| errorMsg | String | 错误信息 |

**响应示例**:

```json
{
  "success": true,
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "errorMsg": null
}
```

**错误响应**:

```json
{
  "success": false,
  "data": null,
  "errorMsg": "验证码错误"
}
```

---

### 1.3 获取当前用户信息

**接口描述**: 获取当前登录用户的详细信息。

**请求信息**:
- **URL**: `/api/auth/me`
- **Method**: `GET`
- **认证**: 需要 Bearer Token

**请求头**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|------|------|------|
| Authorization | String | 是 | Bearer Token，格式：`Bearer {token}` |

**请求示例**:

```bash
curl "http://localhost:8080/api/auth/me" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**响应参数**:

| 参数名 | 类型 | 说明 |
|-------|------|------|
| success | Boolean | 是否成功 |
| data | Object | 用户信息对象 |
| data.id | Long | 用户ID |
| data.phone | String | 手机号 |
| data.nickName | String | 昵称 |
| errorMsg | String | 错误信息 |

**响应示例**:

```json
{
  "success": true,
  "data": {
    "id": 1,
    "phone": "13800138000",
    "nickName": "用户13800138000"
  },
  "errorMsg": null
}
```

**错误响应**:

```json
{
  "success": false,
  "data": null,
  "errorMsg": "未登录"
}
```

---

## 2. 优惠券模块 (Voucher)

### 2.1 创建秒杀券

**接口描述**: 创建秒杀券并预热库存到Redis。

**请求信息**:
- **URL**: `/api/voucher/seckill`
- **Method**: `POST`
- **Content-Type**: `application/json`

**请求参数**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|------|------|------|
| stock | Integer | 是 | 库存数量，必须大于0 |

**请求示例**:

```bash
curl -X POST "http://localhost:8080/api/voucher/seckill" \
  -H "Content-Type: application/json" \
  -d '{
    "stock": 100
  }'
```

**响应参数**:

| 参数名 | 类型 | 说明 |
|-------|------|------|
| success | Boolean | 是否成功 |
| data | Long | 优惠券ID（voucherId） |
| errorMsg | String | 错误信息 |

**响应示例**:

```json
{
  "success": true,
  "data": 1704067200000,
  "errorMsg": null
}
```

**说明**:
- voucherId 使用当前时间戳生成
- 库存会自动预热到 Redis，Key: `seckill:stock:{voucherId}`

---

## 3. 秒杀模块 (Seckill)

### 3.1 秒杀下单

**接口描述**: 执行秒杀下单操作，通过Redis Lua脚本保证原子性。

**请求信息**:
- **URL**: `/api/seckill/{voucherId}`
- **Method**: `POST`
- **认证**: 需要 Bearer Token

**路径参数**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|------|------|------|
| voucherId | Long | 是 | 优惠券ID |

**请求头**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|------|------|------|
| Authorization | String | 是 | Bearer Token，格式：`Bearer {token}` |

**请求示例**:

```bash
curl -X POST "http://localhost:8080/api/seckill/1704067200000" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**响应参数**:

| 参数名 | 类型 | 说明 |
|-------|------|------|
| success | Boolean | 是否成功 |
| data | Long | 订单ID |
| errorMsg | String | 错误信息 |

**响应示例**:

```json
{
  "success": true,
  "data": 1234567890123456789,
  "errorMsg": null
}
```

**错误响应**:

| 错误信息 | 说明 |
|---------|------|
| 未登录 | 未携带Token |
| 登录已过期 | Token无效或过期 |
| 库存不足 | 商品已售罄 |
| 请勿重复下单 | 用户已购买过该商品 |

**执行流程**:

1. 校验登录状态（从Token获取userId）
2. 执行Redis Lua脚本原子扣减库存
3. 生成全局唯一订单ID
4. 发送订单事件到MQ异步处理
5. 返回订单ID

**Lua脚本逻辑**:

```lua
-- KEYS[1]: seckill:stock:{voucherId} 库存key
-- KEYS[2]: seckill:ordered:{voucherId} 已下单用户集合
-- ARGV[1]: userId

-- 检查是否已下单
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return 2  -- 重复下单
end

-- 检查库存
local stock = redis.call('get', KEYS[1])
if not stock or tonumber(stock) <= 0 then
    return 1  -- 库存不足
end

-- 扣减库存
redis.call('decr', KEYS[1])
-- 记录已下单用户
redis.call('sadd', KEYS[2], ARGV[1])
return 0  -- 成功
```

---

## 4. 健康检查模块 (Actuator)

### 4.1 应用健康检查

**接口描述**: 检查应用整体健康状态。

**请求信息**:
- **URL**: `/actuator/health`
- **Method**: `GET`

**请求示例**:

```bash
curl "http://localhost:8080/actuator/health"
```

**响应示例**:

```json
{
  "status": "UP"
}
```

---

### 4.2 Redis健康检查

**接口描述**: 检查Redis连接状态。

**请求信息**:
- **URL**: `/actuator/health/redis`
- **Method**: `GET`

**请求示例**:

```bash
curl "http://localhost:8080/actuator/health/redis"
```

**响应示例**:

```json
{
  "status": "UP",
  "components": {
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.0"
      }
    }
  }
}
```

---

## 5. 数据模型

### 5.1 LoginFormDTO

登录请求对象

| 字段名 | 类型 | 必填 | 说明 |
|-------|------|------|------|
| phone | String | 是 | 手机号 |
| code | String | 是 | 验证码 |

### 5.2 CreateVoucherReq

创建秒杀券请求对象

| 字段名 | 类型 | 必填 | 说明 |
|-------|------|------|------|
| stock | Integer | 是 | 库存数量 |

### 5.3 UserDTO

用户信息对象

| 字段名 | 类型 | 说明 |
|-------|------|------|
| id | Long | 用户ID |
| phone | String | 手机号 |
| nickName | String | 昵称 |

### 5.4 ApiResponse<T>

统一响应对象

| 字段名 | 类型 | 说明 |
|-------|------|------|
| success | Boolean | 是否成功 |
| data | T | 返回数据 |
| errorMsg | String | 错误信息 |

### 5.5 OrderEntity

订单实体

| 字段名 | 类型 | 数据库列名 | 说明 |
|-------|------|-----------|------|
| id | Long | id | 订单ID（主键） |
| userId | Long | user_id | 用户ID |
| voucherId | Long | voucher_id | 优惠券ID |
| status | Integer | status | 订单状态（1=已创建） |
| createdAt | Long | created_at | 创建时间戳 |

**数据库约束**:
- 唯一索引: `uk_user_voucher(user_id, voucher_id)` - 防止重复下单

---

## 6. 错误码说明

### 通用错误

| 错误信息 | HTTP状态码 | 说明 |
|---------|-----------|------|
| 手机号格式不正确 | 200 | 手机号不符合正则 `^1\d{10}$` |
| 验证码错误 | 200 | 验证码不正确或已过期 |
| 未登录 | 200 | 未携带Token |
| 登录已过期 | 200 | Token无效或已过期 |

### 秒杀错误

| 错误信息 | HTTP状态码 | 说明 |
|---------|-----------|------|
| 库存不足 | 200 | 商品已售罄 |
| 请勿重复下单 | 200 | 用户已购买过该商品 |

---

## 附录

### A. Redis Key 说明

| Key Pattern | 类型 | 说明 | TTL |
|-------------|------|------|-----|
| `login:code:{phone}` | String | 验证码 | 2分钟 |
| `login:token:{token}` | Hash | 用户会话信息 | 30分钟 |
| `seckill:stock:{voucherId}` | String | 秒杀券库存 | 永久 |
| `seckill:ordered:{voucherId}` | Set | 已下单用户集合 | 永久 |
| `icr:user` | String | 用户ID自增器 | 永久 |
| `icr:order` | String | 订单ID自增器 | 永久 |

### B. 消息队列配置

#### RabbitMQ

| 配置项 | 值 |
|-------|-----|
| Exchange | `order.events` |
| Queue | `order.created.q` |
| Routing Key | `order.created` |
| Dead Letter Exchange | `order.dlx` |
| Dead Letter Queue | `order.created.dlq` |
| Prefetch | 50 |
| Concurrency | 2-8 |

#### Kafka

| 配置项 | 值 |
|-------|-----|
| Topic | `order.created` |
| Partitions | 6 |
| Consumer Group | `order-consumer-g1` |
| Ack Mode | MANUAL_IMMEDIATE |

### C. 完整调用示例

```bash
# 1. 设置基础URL
export BASE=http://localhost:8080

# 2. 获取验证码
curl -X POST "$BASE/api/auth/code" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "phone=13800138000"

# 3. 登录（替换验证码）
curl -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000","code":"123456"}'

# 4. 设置Token（替换实际token）
export TOKEN="your_token_here"

# 5. 获取用户信息
curl "$BASE/api/auth/me" \
  -H "Authorization: Bearer $TOKEN"

# 6. 创建秒杀券
curl -X POST "$BASE/api/voucher/seckill" \
  -H "Content-Type: application/json" \
  -d '{"stock": 100}'

# 7. 秒杀下单（替换voucherId）
curl -X POST "$BASE/api/seckill/1704067200000" \
  -H "Authorization: Bearer $TOKEN"
```

---

**文档版本**: v1.0
**最后更新**: 2024年
