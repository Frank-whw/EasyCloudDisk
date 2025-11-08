# EasyCloudDisk 接口文档

## 1. 概述

本文档描述了 EasyCloudDisk 系统的完整 API 接口规范，包括用户认证、文件操作等核心功能。系统采用 RESTful 设计，使用 JWT 进行身份认证，数据格式统一为 JSON。

### 基础信息
- **基础路径**: `http://localhost:8080`
- **认证方式**: JWT (JSON Web Token)
- **数据格式**: JSON
- **统一响应格式**: `ApiResponse<T>`

### 统一响应格式
所有接口返回统一格式的响应数据：

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
- `data`: Object 类型，返回的具体数据（可为 null）
- `code`: Integer 类型，业务状态码

## 2. 认证接口

### 2.1 用户注册

**接口地址**: `POST /auth/register`

**功能描述**: 新用户注册，创建用户账户

**请求参数**:

| 参数名称 | 类型 | 必填 | 说明 |
|---------|------|------|------|
| email | String | 是 | 用户邮箱，需符合邮箱格式 |
| password | String | 是 | 用户密码，长度6-20个字符 |

**请求示例**:
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**响应参数**:

| 参数名称 | 类型 | 说明 |
|---------|------|------|
| userId | String | 用户唯一标识 |
| email | String | 用户邮箱 |
| token | String | JWT 认证令牌 |

**成功响应示例**:
```json
{
  "success": true,
  "message": "注册成功",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiaWF0IjoxNjE2MjM5MDIyfQ.5c5d8b8c4c4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b"
  },
  "code": 200
}
```

**错误响应**:
- `400`: 邮箱已存在 (`EMAIL_EXISTS`)
- `400`: 参数验证失败

### 2.2 用户登录

**接口地址**: `POST /auth/login`

**功能描述**: 用户登录，获取访问令牌

**请求参数**:

| 参数名称 | 类型 | 必填 | 说明 |
|---------|------|------|------|
| email | String | 是 | 用户邮箱 |
| password | String | 是 | 用户密码 |

**请求示例**:
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**响应参数**: 同注册接口

**成功响应示例**:
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiaWF0IjoxNjE2MjM5MDIyfQ.5c5d8b8c4c4b4b4b4b4b4b4b4b4b4b4b4b4b4b"
  },
  "code": 200
}
```

**错误响应**:
- `401`: 用户名或密码错误 (`INVALID_CREDENTIALS`)

## 3. 文件操作接口

所有文件操作接口都需要在请求头中包含认证信息：
```
Authorization: Bearer <token>
```

### 3.1 获取文件列表

**接口地址**: `GET /files`

**功能描述**: 获取当前用户的所有文件列表

**请求参数**: 无

**响应参数**:

| 参数名称 | 类型 | 说明 |
|---------|------|------|
| fileId | String | 文件唯一标识 |
| userId | String | 文件所属用户ID |
| name | String | 文件名 |
| filePath | String | 文件路径 |
| s3Key | String | S3存储键 |
| fileSize | Long | 文件大小（字节） |
| contentHash | String | 文件内容哈希 |
| createdAt | String | 创建时间（ISO8601格式） |
| updatedAt | String | 更新时间（ISO8601格式） |

**成功响应示例**:
```json
{
  "success": true,
  "message": "列表成功",
  "data": [
    {
      "fileId": "660e8400-e29b-41d4-a716-446655440001",
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "name": "document.pdf",
      "filePath": "/documents/",
      "s3Key": "user-550e8400/document.pdf",
      "fileSize": 1024576,
      "contentHash": "a1b2c3d4e5f6...",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z"
    }
  ],
  "code": 200
}
```

### 3.2 文件上传

**接口地址**: `POST /files/upload`

**功能描述**: 上传文件到云存储

**请求参数**:

| 参数名称 | 类型 | 必填 | 说明 |
|---------|------|------|------|
| file | File | 是 | 要上传的文件 |
| filePath | String | 否 | 文件存储路径 |

**请求格式**: `multipart/form-data`

**响应参数**:

| 参数名称 | 类型 | 说明 |
|---------|------|------|
| fileId | String | 文件唯一标识 |
| name | String | 文件名 |
| fileSize | Long | 文件大小 |
| filePath | String | 文件路径 |

**成功响应示例**:
```json
{
  "success": true,
  "message": "上传成功",
  "data": {
    "fileId": "660e8400-e29b-41d4-a716-446655440001",
    "name": "document.pdf",
    "fileSize": 1024576,
    "filePath": "/documents/"
  },
  "code": 200
}
```

**错误响应**:
- `409`: 文件重复 (`DUPLICATE_FILE`)

### 3.3 文件下载

**接口地址**: `GET /files/{fileId}/download`

**功能描述**: 下载指定文件

**路径参数**:

| 参数名称 | 类型 | 必填 | 说明 |
|---------|------|------|------|
| fileId | String | 是 | 文件唯一标识 |

**响应**: 文件二进制流 (`application/octet-stream`)

**错误响应**:
- `404`: 文件不存在 (`FILE_NOT_FOUND`)

### 3.4 文件删除

**接口地址**: `DELETE /files/{fileId}`

**功能描述**: 删除指定文件

**路径参数**:

| 参数名称 | 类型 | 必填 | 说明 |
|---------|------|------|------|
| fileId | String | 是 | 文件唯一标识 |

**响应参数**: 无（data 为 null）

**成功响应示例**:
```json
{
  "success": true,
  "message": "删除成功",
  "data": null,
  "code": 200
}
```

**错误响应**:
- `404`: 文件不存在 (`FILE_NOT_FOUND`)

## 4. 数据模型定义

### 4.1 认证相关模型

#### AuthRequest (认证请求)
```java
public class AuthRequest {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式错误")
    private String email;
    
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20个字符之间")
    private String password;
}
```

#### AuthResponse (认证响应)
```java
public class AuthResponse {
    private String userId;
    private String email;
    private String message;
    private String token;
}
```

### 4.2 文件相关模型

#### FileMetadata (文件元数据)
```java
public class FileMetadata {
    private String fileId;
    private String userId;
    private String fileName;
    private String filePath;
    private String s3_key;
    private String contentHash;
    private long fileSize;
    private long lastModified;
}
```

#### ApiResponse (统一响应)
```java
public class ApiResponse<T> {
    private String message;
    private T data;
    private Boolean success;
    private int code;
}
```

## 5. 错误码定义

| 错误码 | 描述 | 说明 |
|--------|------|------|
| 200 | OK | 操作成功 |
| 400 | EMAIL_EXISTS | 注册时邮箱已存在 |
| 401 | INVALID_CREDENTIALS | 登录凭据错误或未认证访问 |
| 403 | FORBIDDEN | 无权限访问资源 |
| 404 | FILE_NOT_FOUND | 文件不存在 |
| 409 | DUPLICATE_FILE | 文件去重冲突 |
| 500 | INTERNAL_ERROR | 服务器内部错误 |

## 6. 客户端 API 调用示例

### 6.1 认证客户端使用示例

```java
// 创建认证客户端
AuthApiClient authClient = new AuthApiClient("http://localhost:8080");

// 用户注册
AuthResponse registerResponse = authClient.register("user@example.com", "password123");
if (registerResponse != null) {
    String token = registerResponse.getToken();
    String userId = registerResponse.getUserId();
    System.out.println("注册成功，用户ID: " + userId);
}

// 用户登录
AuthResponse loginResponse = authClient.login("user@example.com", "password123");
if (loginResponse != null) {
    String token = loginResponse.getToken();
    System.out.println("登录成功，获取令牌: " + token);
}
```

### 6.2 文件客户端使用示例

```java
// 创建文件客户端并设置认证令牌
FileApiClient fileClient = new FileApiClient("http://localhost:8080");
fileClient.setAuthToken(token);

// 获取文件列表
List<FileMetadata> files = fileClient.listFiles();
System.out.println("用户文件数量: " + (files != null ? files.size() : 0));

// 上传文件
Path filePath = Paths.get("/path/to/file.pdf");
FileMetadata uploadedFile = fileClient.uploadFile(filePath, "/documents/");
if (uploadedFile != null) {
    System.out.println("文件上传成功，ID: " + uploadedFile.getFileId());
}

// 下载文件
String fileId = "660e8400-e29b-41d4-a716-446655440001";
Path downloadPath = Paths.get("/path/to/download/file.pdf");
boolean downloadSuccess = fileClient.downloadFile(fileId, downloadPath);
if (downloadSuccess) {
    System.out.println("文件下载成功");
}

// 删除文件
boolean deleteSuccess = fileClient.deleteFile(fileId);
if (deleteSuccess) {
    System.out.println("文件删除成功");
}
```

## 7. 接口调用流程

### 7.1 完整用户流程

```mermaid
graph TD
    A[客户端启动] --> B[用户登录/注册]
    B --> C{认证成功?}
    C -->|是| D[获取文件列表]
    C -->|否| E[显示错误信息]
    D --> F[显示用户文件]
    F --> G{用户操作}
    G -->|上传文件| H[调用上传接口]
    G -->|下载文件| I[调用下载接口]
    G -->|删除文件| J[调用删除接口]
    H --> K[更新本地列表]
    I --> L[保存到本地]
    J --> M[更新本地列表]
```

### 7.2 同步流程

```mermaid
graph TD
    A[监听文件变化] --> B{变化类型}
    B -->|创建| C[上传新文件]
    B -->|修改| D[上传更新]
    B -->|删除| E[删除服务器文件]
    C --> F[更新本地状态]
    D --> F
    E --> F
    F --> G[等待下次变化]
```

## 8. 安全说明

### 8.1 认证安全
- 使用 JWT 进行身份认证
- Token 有效期为 24 小时
- 所有文件操作都需要有效的 JWT Token

### 8.2 数据安全
- 文件存储使用 AWS S3 对象存储
- 支持文件级去重，避免重复存储
- 文件内容使用哈希值进行完整性校验

### 8.3 传输安全
- 建议使用 HTTPS 进行数据传输
- 敏感信息（如密码）在传输过程中已加密

## 9. 性能优化

### 9.1 连接池优化
- HTTP 客户端使用连接池，提高请求效率
- 支持并发请求处理

### 9.2 文件处理优化
- 支持文件压缩传输
- 实现文件去重机制
- 支持断点续传（待实现）

## 10. 扩展功能

### 10.1 已实现功能
- ✅ 用户注册/登录
- ✅ JWT 身份认证
- ✅ 文件上传/下载/列表/删除
- ✅ 文件元数据管理
- ✅ 文件监听和同步
- ✅ 文件压缩
- ✅ 文件去重

### 10.2 待实现功能
- 🔄 增量同步（差分同步）
- 🔄 断点续传
- 🔄 文件版本控制
- 🔄 冲突解决机制
- 🔄 文件共享功能

---

**文档版本**: v1.0  
**最后更新**: 2024年1月  
**维护团队**: EasyCloudDisk 开发团队