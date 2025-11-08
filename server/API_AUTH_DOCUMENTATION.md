# EasyCloudDisk 登录注册接口文档

## 1. 概述

本文档描述了 EasyCloudDisk 系统的用户认证接口，包括用户注册和用户登录两个核心功能。

## 2. 接口列表

| 接口名称 | 请求方式 | 接口地址 | 说明 |
|---------|---------|---------|-----|
| 用户注册 | POST | `/auth/register` | 用户注册接口 |
| 用户登录 | POST | `/auth/login` | 用户登录接口 |

## 3. 公共规范

### 3.1 请求规范

所有接口均使用 JSON 格式传输数据，请求头需包含：
```
Content-Type: application/json
```

### 3.2 响应规范

所有接口返回的数据格式统一采用以下结构：

```json
{
  "success": true,
  "message": "操作成功",
  "data": {},
  "code": 200
}
```

字段说明：
- `success`: Boolean 类型，表示请求是否成功
- `message`: String 类型，返回的消息说明
- `data`: Object 类型，返回的具体数据
- `code`: Integer 类型，业务状态码

## 4. 接口详情

### 4.1 用户注册接口

#### 接口地址
`POST /auth/register`

#### 请求参数

| 参数名称 | 类型 | 必填 | 说明 |
|---------|------|------|------|
| email | String | 是 | 用户邮箱，需符合邮箱格式 |
| password | String | 是 | 用户密码，长度在6-20个字符之间 |

示例：
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

#### 响应参数

成功时返回：
```json
{
  "success": true,
  "message": "注册成功",
  "data": {
    "userId": "用户唯一标识符",
    "email": "用户邮箱",
    "message": "认证成功",
    "token": "JWT认证令牌"
  },
  "code": 200
}
```

失败时可能的返回：
```json
{
  "success": false,
  "message": "注册失败xxx",
  "data": null,
  "code": 400
}
```

#### 错误码说明

| 错误码 | 说明 |
|-------|------|
| 200 | 注册成功 |
| 400 | 注册失败，如邮箱已存在或参数校验失败 |

#### 验证规则

1. 邮箱不能为空且必须符合邮箱格式
2. 密码不能为空，长度必须在6-20个字符之间
3. 邮箱不能重复，若已被注册则返回错误

### 4.2 用户登录接口

#### 接口地址
`POST /auth/login`

#### 请求参数

| 参数名称 | 类型 | 必填 | 说明 |
|---------|------|------|------|
| email | String | 是 | 用户邮箱 |
| password | String | 是 | 用户密码 |

示例：
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

#### 响应参数

成功时返回：
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "userId": "用户唯一标识符",
    "email": "用户邮箱",
    "message": "认证成功",
    "token": "JWT认证令牌"
  },
  "code": 200
}
```

失败时可能的返回：
```json
{
  "success": false,
  "message": "登录失败xxx",
  "data": null,
  "code": 400
}
```

#### 错误码说明

| 错误码 | 说明 |
|-------|------|
| 200 | 登录成功 |
| 400 | 登录失败，如用户名或密码错误 |

#### JWT Token 使用说明

登录成功后，后端会返回一个 JWT Token，前端需要在后续请求的 Header 中添加：
```
Authorization: Bearer {token}
```

## 5. 使用示例

### 5.1 注册示例

请求：
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "123456"
  }'
```

响应：
```json
{
  "success": true,
  "message": "注册成功",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "test@example.com",
    "message": "认证成功",
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  },
  "code": 200
}
```

### 5.2 登录示例

请求：
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "123456"
  }'
```

响应：
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "test@example.com",
    "message": "认证成功",
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  },
  "code": 200
}
```

## 6. 注意事项

1. 所有密码在传输过程中应当通过 HTTPS 加密
2. JWT Token 默认有效期为24小时
3. 密码在数据库中以加密形式存储
4. 接口支持跨域请求
5. 请求参数会进行有效性验证