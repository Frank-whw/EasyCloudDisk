# Cloud Disk - 云盘系统

基于 Spring Boot + AWS S3 的云存储系统，支持文件上传、下载、去重等功能。

**项目状态**: 已部署到 AWS EC2 + S3

---

## 快速开始


### 方式一：使用已部署的云端服务（推荐）

**后端服务已部署**: http://54.95.61.230:8080

1. **启动本地 HTTP 服务器（必需，避免 CORS 错误）**
   ```powershell
   # 在 PowerShell 中执行
   cd D:\Vs_C\Cloud\client
   python -m http.server 3000
   ```
   
   或者使用 Node.js:
   ```powershell
   cd D:\Vs_C\Cloud\client
   npx http-server -p 3000
   ```

2. **打开前端界面**
   
   在浏览器访问: **http://localhost:3000**
   
   **注意**: 不要直接双击 `index.html` 文件，会出现跨域错误！

2. **API 测试**
   ```bash
   # 健康检查
   curl http://54.95.61.230:8080/health
   
   # 注册用户
   curl -X POST http://54.95.61.230:8080/auth/register \
     -H "Content-Type: application/json" \
     -d '{"email":"test@example.com","password":"Test123456"}'
   
   # 登录
   curl -X POST http://54.95.61.230:8080/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"test@example.com","password":"Test123456"}'

   # 响应示例（所有 API 都使用统一的包装结构，code 为字符串枚举）
   # {
   #   "success": true,
   #   "message": "登录成功",
   #   "code": "SUCCESS",
   #   "data": {
   #     "userId": "...",
   #     "email": "test@example.com",
   #     "token": "<JWT>",
   #     "refreshToken": "<JWT>"
   #   }
   # }
   ```

### 方式二：本地启动后端服务

1. **克隆项目并进入服务器目录**
   ```bash
   cd Cloud/server
   ```

2. **配置 AWS 凭证**
   
   创建或编辑 `~/.aws/credentials`:
   ```ini
   [default]
   aws_access_key_id = YOUR_ACCESS_KEY
   aws_secret_access_key = YOUR_SECRET_KEY
   ```
   
   或者设置环境变量:
   ```bash
   export AWS_ACCESS_KEY_ID=your_access_key
   export AWS_SECRET_ACCESS_KEY=your_secret_key
   ```

3. **创建 S3 Bucket**
   ```bash
   aws s3 mb s3://your-bucket-name --region ap-northeast-1
   ```

4. **编译项目**
   ```bash
   mvn clean package -DskipTests
   ```

5. **启动服务**
   ```bash
   java -jar target/clouddisk-server-1.0.0.jar \
     --jwt.secret=your-super-secret-key-must-be-at-least-256-bits-long \
     --aws.region=ap-northeast-1 \
     --aws.s3.bucket-name=your-bucket-name \
     --spring.profiles.active=dev
   ```

   服务将在 `http://localhost:8080` 启动

   **CORS 配置**

   为了避免在开发环境中出现跨域凭证错误，后端提供了新的环境变量用于覆盖默认配置：

   | 环境变量 | 说明 | 默认值 |
   | --- | --- | --- |
   | `APP_CORS_ALLOWED_ORIGINS` | 允许访问的前端域名（多个值使用逗号分隔） | `http://localhost:3000` |
   | `APP_CORS_ALLOW_CREDENTIALS` | 是否向指定来源发送 Cookie / Authorization 凭证 | `false` |

   当使用通配符 `*` 作为来源时，`allow-credentials` 会被强制关闭以符合浏览器安全策略。

6. **验证服务**
   ```bash
   # 健康检查
   curl http://localhost:8080/health
   
   # 应返回: {"status":"UP","timestamp":...}
   ```

### 方式三：前端连接本地服务

如果你在本地启动了后端服务，需要修改前端配置:

1. **修改 API 地址**
   
   编辑 `client/index.html`，找到第 276 行:
   ```javascript
   const API_BASE_URL = 'http://54.95.61.230:8080';
   ```
   
   改为:
   ```javascript
   const API_BASE_URL = 'http://localhost:8080';
   ```

2. **打开前端**
   ```bash
   # 直接打开
   open client/index.html  # macOS
   start client/index.html # Windows
   
   # 或使用 HTTP 服务器
   cd client
   python3 -m http.server 3000
   # 访问 http://localhost:3000
   ```

---

## 完整测试流程

### 1. 使用前端界面测试（推荐）

1. **打开前端**: `client/index.html`
2. **注册账号**: 输入邮箱和密码(至少8位，包含字母和数字)
3. **登录系统**: 使用刚注册的账号登录
4. **上传文件**: 点击上传区域选择文件
5. **查看文件**: 文件列表会自动刷新
6. **下载文件**: 点击"下载"按钮
7. **删除文件**: 点击"删除"按钮

### 2. 使用 API 测试

```bash
# 设置服务器地址（根据实际情况修改）
API_URL="http://54.95.61.230:8080"  # 云端
# API_URL="http://localhost:8080"   # 本地

# 1. 注册用户
curl -X POST $API_URL/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","password":"Demo123456"}'

# 2. 登录获取 Token
TOKEN=$(curl -s -X POST $API_URL/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","password":"Demo123456"}' \
  | grep -o '"token":"[^"]*' | cut -d'"' -f4)

echo "Token: $TOKEN"

# 3. 创建测试文件
echo "Hello Cloud Disk!" > test.txt

# 4. 上传文件
curl -X POST $API_URL/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@test.txt" \
  -F "filePath=/test.txt"

# 5. 获取文件列表
curl -s $API_URL/files \
  -H "Authorization: Bearer $TOKEN" | jq .

# 6. 提取文件 ID
FILE_ID=$(curl -s $API_URL/files \
  -H "Authorization: Bearer $TOKEN" \
  | grep -o '"fileId":"[^"]*' | head -1 | cut -d'"' -f4)

echo "File ID: $FILE_ID"

# 7. 下载文件
curl -X GET "$API_URL/files/$FILE_ID/download" \
  -H "Authorization: Bearer $TOKEN" \
  -o downloaded.txt

# 8. 验证下载的文件
cat downloaded.txt

# 9. 删除文件
curl -X DELETE "$API_URL/files/$FILE_ID" \
  -H "Authorization: Bearer $TOKEN"
```

### 3. 测试文件去重功能

```bash
# 1. 上传第一个文件
echo "Duplicate test" > file1.txt
curl -X POST $API_URL/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@file1.txt" \
  -F "filePath=/file1.txt"

# 2. 上传相同内容的文件（应该秒传）
echo "Duplicate test" > file2.txt
curl -X POST $API_URL/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@file2.txt" \
  -F "filePath=/file2.txt"

# 3. 查看文件列表（两个文件应该共享同一个 S3 Key）
curl -s $API_URL/files \
  -H "Authorization: Bearer $TOKEN" | jq '.data[] | {name, s3Key}'
```

---

## 项目架构

```
Cloud/
├── server/              # 后端服务 (Spring Boot)
│   ├── src/main/java/com/clouddisk/
│   │   ├── common/      # 公共模块（安全、异常处理）
│   │   ├── user/        # 用户模块（注册、登录）
│   │   ├── file/        # 文件模块（上传、下载、去重）
│   │   └── storage/     # 存储模块（S3 集成）
│   └── pom.xml
├── client/              # 前端界面 (HTML + JS)
│   ├── index.html       # 单页面应用
│   └── README.md        # 前端使用说明
├── README.md            # 项目说明（本文件）
├── PROGRESS.md          # 开发进度
├── ANALYSIS.md          # 技术分析
└── QUICKSTART.md        # 快速开始指南
```

---

## 核心功能

- ✅ 用户注册/登录 (JWT 认证)
- ✅ 文件上传/下载/删除
- ✅ 文件列表查询
- ✅ 文件级去重（秒传）
- ✅ 引用计数删除（智能清理 S3）
- ✅ SHA-256 哈希计算
- ✅ AWS S3 云存储集成
- ✅ 现代化 Web 前端界面

---

## 技术栈

**后端**:
- Spring Boot 3.2.0
- Spring Security + JWT
- Spring Data JPA
- H2 Database (内存)
- AWS SDK for Java 2.x
- Maven

**前端**:
- HTML5 + CSS3 + JavaScript
- Fetch API
- LocalStorage

**云服务**:
- AWS EC2 (计算)
- AWS S3 (存储)

---

## 配置说明

### application.yml 主要配置

```yaml
# JWT 配置
jwt:
  secret: ${JWT_SECRET:your-secret-key}
  expiration: 86400000  # 24小时

# AWS 配置
aws:
  region: ${AWS_REGION:ap-northeast-1}
  s3:
    bucket-name: ${S3_BUCKET_NAME}

# 数据库配置
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: update
```

---

## 常见问题

### Q: 前端显示 "网络错误: Failed to fetch"?
A: 这是 CORS 跨域问题。解决方法:
1. **不要直接双击打开 HTML 文件**（会使用 `file://` 协议）
2. 使用 HTTP 服务器打开:
   ```powershell
   cd D:\Vs_C\Cloud\client
   python -m http.server 3000
   ```
3. 在浏览器访问: http://localhost:3000

### Q: 无法连接到服务器?
A: 检查:
1. 服务是否正常启动: `curl http://54.95.61.230:8080/health`
2. 网络连接是否正常
3. API_BASE_URL 配置是否正确（在 index.html 第 276 行）

### Q: 文件上传失败?
A: 检查:
1. AWS 凭证是否配置正确
2. S3 Bucket 是否存在
3. 文件大小是否超过 100MB
4. Token 是否有效（未过期）

### Q: 服务重启后数据丢失?
A: 当前使用 H2 内存数据库，重启后数据会丢失。生产环境建议使用持久化数据库（PostgreSQL/MySQL）。

---

group hw for 'Cloud computing system'

## 数据库结构
```sql
-- 用户表（补充created_at和updated_at的数据类型为TIMESTAMP）
CREATE TABLE users (
    user_id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,  -- 邮箱通常设为唯一
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- 默认当前时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP   -- 默认当前时间
);

-- 文件表（补充file_path类型、name长度，修正updated_at）
CREATE TABLE files (
    file_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,  -- 文件名建议指定长度
    file_path VARCHAR(1024),     -- 文件路径
    s3_key VARCHAR(1024) NOT NULL,         -- S3存储键
    file_size BIGINT,
    content_hash VARCHAR(64),              -- 用于去重（如MD5/SHA256）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 关联users表的外键（确保file属于存在的用户，可选但推荐）
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

```

## 接口文档

- 基础路径：`/` 服务器端口：`8080`
- 认证方式：`JWT`（登录后返回 `token`，在后续请求头 `Authorization: Bearer <token>`）
- 统一响应：`ApiResponse<T>`
  - `success`: `boolean` 成功标识
  - `message`: `string` 文本消息
  - `code`: `number` 业务码（成功`200`，常见错误见下）
  - `data`: `T | null` 具体数据

### 认证模块

- `POST /auth/register`
  - 描述：用户注册
  - 请求
    - Headers：`Content-Type: application/json`
    - Body：
      ```json
      { "email": "string", "password": "string(6-20)" }
      ```
  - 响应
    - 200：
      ```json
      { "success": true, "message": "注册成功", "code": 200, "data": { "userId": "string", "email": "string", "token": "jwt" } }
      ```
    - 400：`EMAIL_EXISTS`（邮箱已存在）

- `POST /auth/login`
  - 描述：用户登录
  - 请求
    - Headers：`Content-Type: application/json`
    - Body：
      ```json
      { "email": "string", "password": "string" }
      ```
  - 响应
    - 200：
      ```json
      { "success": true, "message": "登录成功", "code": 200, "data": { "userId": "string", "email": "string", "token": "jwt" } }
      ```
    - 401：`INVALID_CREDENTIALS`（账户或密码错误）

### 文件模块（需认证）

- `GET /files`
  - 描述：获取当前用户文件列表
  - 请求
    - Headers：`Authorization: Bearer <token>`
  - 响应
    - 200：
      ```json
      { "success": true, "message": "列表成功", "code": 200, "data": [
        { "fileId": "string", "userId": "string", "name": "string", "filePath": "string", "s3Key": "string", "fileSize": 123, "contentHash": "string", "createdAt": "ISO8601", "updatedAt": "ISO8601" }
      ] }
      ```

- `POST /files/upload`
  - 描述：上传文件
  - 请求
    - Headers：`Authorization: Bearer <token>`
    - Content-Type：`multipart/form-data`
    - Form fields：`file`（二进制文件）、`filePath`（可选逻辑路径）
  - 响应
    - 200：
      ```json
      { "success": true, "message": "上传成功", "code": 200, "data": { "fileId": "string", "name": "string", "fileSize": 123, "filePath": "string" } }
      ```
    - 409：`DUPLICATE_FILE`（基于`contentHash`的文件级去重命中）

- `GET /files/{fileId}/download`
  - 描述：下载文件内容
  - 请求
    - Headers：`Authorization: Bearer <token>`
  - 响应
    - 200：`application/octet-stream` 二进制流
    - 404：`FILE_NOT_FOUND`

- `DELETE /files/{fileId}`
  - 描述：删除文件（同时删除对象存储记录）
  - 请求
    - Headers：`Authorization: Bearer <token>`
  - 响应
    - 200：
      ```json
      { "success": true, "message": "删除成功", "code": 200 }
      ```
    - 404：`FILE_NOT_FOUND`

### 高级上传功能

#### 秒传（文件级去重）

- `POST /files/quick-check`
  - 描述：检查文件是否可以秒传
  - 请求：
    ```json
    { "hash": "sha256-hash-of-file" }
    ```
  - 响应：
    ```json
    { "success": true, "data": { "canQuickUpload": true } }
    ```

- `POST /files/quick-upload`
  - 描述：执行秒传（无需上传文件内容）
  - 请求：
    ```json
    { "hash": "sha256-hash", "fileName": "example.pdf", "path": "/documents", "fileSize": 1024000 }
    ```
  - 响应：
    ```json
    { "success": true, "message": "秒传成功", "data": { "fileId": "...", "name": "example.pdf", ... } }
    ```

#### 断点续传

- `POST /files/resumable/init`
  - 描述：初始化断点续传会话
  - 请求：
    ```json
    { "fileName": "large-file.zip", "path": "/", "fileSize": 104857600 }
    ```
  - 响应：
    ```json
    { "success": true, "data": { "sessionId": "uuid", "totalChunks": 50, "uploadedChunks": 0, "status": "ACTIVE" } }
    ```

- `POST /files/resumable/{sessionId}/chunk/{chunkIndex}`
  - 描述：上传单个分块（2MB）
  - 请求：`multipart/form-data` with `chunk` field
  - 响应：
    ```json
    { "success": true, "data": { "sessionId": "uuid", "uploadedChunks": 25, "totalChunks": 50 } }
    ```

- `POST /files/resumable/{sessionId}/complete`
  - 描述：完成断点续传，合并所有分块
  - 响应：
    ```json
    { "success": true, "message": "文件上传完成", "data": { "fileId": "...", ... } }
    ```

- `GET /files/resumable/sessions`
  - 描述：获取用户所有上传会话
  - 响应：会话列表（包含进度信息）

#### 差分同步（增量同步）

- `GET /files/{fileId}/signatures`
  - 描述：获取文件的块签名列表（用于客户端比对）
  - 响应：
    ```json
    { "success": true, "data": [
      { "chunkIndex": 0, "hash": "sha256-hash", "size": 4194304, "offset": 0 },
      { "chunkIndex": 1, "hash": "sha256-hash", "size": 4194304, "offset": 4194304 }
    ] }
    ```

- `POST /files/{fileId}/delta`
  - 描述：应用差分更新（仅上传变更的块）
  - 请求：包含变更块的数据（需特殊编码处理二进制）
  - 响应：
    ```json
    { "success": true, "message": "差分更新成功", "data": { "fileId": "...", "version": 2, ... } }
    ```

### 客户端同步建议
- **文件系统监控**: 使用Java WatchService监听本地目录变更
- **秒传优化**: 上传前先计算SHA-256，调用quick-check接口
- **大文件处理**: 使用断点续传，支持暂停/恢复
- **增量更新**: 文件修改时，获取服务器签名，仅上传变更块

### 错误代码定义
- `200 OK` 成功
- `400 EMAIL_EXISTS` 注册时邮箱已存在
- `401 INVALID_CREDENTIALS` 登录凭据错误或未认证访问
- `403 FORBIDDEN` 无权限访问资源
- `404 FILE_NOT_FOUND` 文件不存在
- `409 DUPLICATE_FILE` 文件去重冲突
- `500 INTERNAL_ERROR` 服务器内部错误

## Lab要求--用java做一个简易网盘

### 1. 云端部署 
- [x] 找到一个可用的云（AWS学生版）
- [x] 使用云对象存储（AWS S3: `clouddisk-test-1762861672`）
- [x] 业务逻辑部署在云虚拟机（AWS EC2: `54.95.61.230:8080`）

### 2. 基础服务与多用户支撑 

#### 2.1 基础文件操作 
- [x] **上传**: 支持单文件上传，带SHA-256哈希计算
- [x] **下载**: 支持块重组下载，自动解压缩（已测试验证）
- [x] **列表**: 支持按路径查询文件列表
- [x] **删除**: 支持引用计数删除，自动清理未引用块（已测试验证）

#### 2.2 用户认证和多用户管理 
- [x] 用户注册/登录系统（邮箱+密码）
- [x] JWT Token 身份验证（HS384算法，24小时过期）
- [x] 每个用户只能访问自己的文件（通过userId隔离）

#### 2.3 客户端自动同步 
- [ ] 本地同步文件夹实现
- [ ] 文件系统事件监控（创建、修改、删除、重命名）
- [ ] 自动同步到服务器
- [ ] 服务器变更通知/拉取机制

### 3. 网络流量优化技术

#### 3.1 数据压缩 
- [x] **服务端压缩**: 上传时对每个块进行GZIP压缩（如果压缩后更小）
- [x] **自动解压**: 下载时根据元数据自动解压缩
- [x] **存储优化**: S3存储压缩后的数据，元数据标记压缩状态

#### 3.2 文件级去重  **（秒传功能 - 测试已通过）**
- [x] 计算文件哈希值（SHA-256）
- [x] 上传前检查哈希是否存在（API: POST /files/quick-check）
- [x] 存在时创建指针而非重复存储（API: POST /files/quick-upload）
- [x] **秒传机制**: 如果服务器已有相同哈希的文件，无需传输数据，直接复制引用

#### 3.3 块级去重  **（已完整实现并测试）**
- [x] **块切分**: 固定4MB块大小切分大文件
- [x] **哈希计算**: 每个块计算SHA-256哈希
- [x] **去重存储**: 仅上传服务器不存在的新块（已测试验证）
- [x] **引用计数**: 自动管理块引用，删除时智能清理（已测试验证）
- [x] **元数据组装**: 通过`file_chunk_mappings`表记录文件与块的关系

**测试验证数据**:
```
- 上传2个相同文件(testfile1.txt, testfile2.txt, 55字节)
  → S3仅存储1个块，ref_count=6 
- 上传新文件(test_download.txt, 39字节)
  → S3新增1个块，ref_count=2 
- 下载文件验证: diff检查通过 
- 删除文件: ref_count从2降到1，块未删除（仍有引用）
```
### 4. 高级优化技术 

#### 4.1 差分同步（增量同步） **（测试已通过）**
- [x] **块签名获取**: GET /files/{fileId}/signatures 获取文件所有块的哈希 
- [x] **差异检测**: 客户端比对本地和服务器块签名，识别变更块
- [x] **增量上传**: POST /files/{fileId}/delta 仅上传变更的块
- [x] **服务器合并**: 复用未变更块，应用变更块，生成新版本
- [x] 适用场景：大文件频繁小改动（如文档编辑、日志追加）


#### 4.2 断点续传  **（测试已通过）**
- [x] **会话管理**: POST /files/resumable/init 创建上传会话
- [x] **分块上传**: POST /files/resumable/{sessionId}/chunk/{index} 上传单个2MB分块
- [x] **进度跟踪**: GET /files/resumable/sessions 查看所有上传会话及进度
- [x] **断点恢复**: 中断后可继续上传未完成的分块
- [x] **自动清理**: 24小时后自动清理过期会话
- [x] **完成合并**: POST /files/resumable/{sessionId}/complete 合并所有分块

#### 4.3 数据加密  **（服务端支持完成）**
- [x] **客户端加密支持**: 服务端存储加密元数据
- [x] **加密算法**: 支持 AES-256-GCM、AES-256-CBC
- [x] **密钥派生**: 支持 PBKDF2、Argon2
- [x] **元数据管理**: 存储加密算法、盐值、IV、迭代次数
- [x] **收敛加密**: 支持收敛加密模式（可去重但安全性较低）
- [x] **完整性校验**: 存储原始文件哈希用于验证

**API支持**:
- `POST /files/upload-encrypted` - 上传客户端加密文件
- `GET /files/{fileId}/encryption` - 获取加密元数据
- `POST /files/convergent-check` - 检查收敛加密文件秒传

**加密方案设计**:
> - **方案1（标准加密）**: 客户端加密，服务端存储密文，**无法去重**，安全性高
> - **方案2（收敛加密）**: 相同明文+固定密钥=相同密文，**支持去重**，安全性较低
> - **推荐**: 让用户选择，重要文件用标准加密，普通文件用收敛加密或不加密

**客户端实现要求**:
```javascript
// 1. 生成密钥（从用户密码派生）
key = PBKDF2(password, salt, iterations=100000)

// 2. 加密文件
encryptedData = AES-256-GCM.encrypt(fileData, key, iv)

// 3. 上传
POST /files/upload-encrypted
FormData: {
  file: encryptedData,
  metadata: {
    algorithm: "AES-256-GCM",
    keyDerivation: "PBKDF2",
    salt: base64(salt),
    iterations: 100000,
    iv: base64(iv),
    convergent: false,
    originalHash: sha256(fileData),
    originalSize: fileData.length,
    encryptedSize: encryptedData.length
  }
}
```

### 5. 文件共享与协同 
- [x] **文件版本控制**（历史版本保留）
  - 上传文件时自动保存旧版本
  - 支持查询历史版本列表
  - FileVersion实体完整记录版本信息
- [x] **冲突解决策略**（同时修改处理）
  - USE_LOCAL: 保留本地版本
  - USE_REMOTE: 使用远程版本
  - KEEP_BOTH: 保留两个版本（重命名）
  - USE_NEWER: 根据修改时间自动选择
- [x] **共享功能**（文件/文件夹权限管理）
  - SharedResource实体完整记录共享关系
  - CollaborationService提供共享业务逻辑
  - CollaborationController提供REST API接口
  - 支持READ/WRITE权限控制
  - 支持文件和目录共享（包含子树）
  - 支持共享过期时间设置
  - 支持列出“与我共享”的文件

---

## 完成度总结

| 模块 | 完成度 | 说明 |
|-----|--------|------|
| 云端部署 | 100%  | AWS EC2 + S3 完整部署 |
| 基础文件操作 | 100%  | 上传/下载/列表/删除全部测试通过 |
| 用户认证 | 100%  | JWT认证，多用户隔离 |
| 块级去重 | 100%  | 完整实现+测试验证 |
| 数据压缩 | 100%  | GZIP压缩+自动解压 |
| 文件级去重 | 100%  | 秒传功能+测试验证 |
| 差分同步 | 100%  | 块签名增量同步+测试验证 |
| 断点续传 | 100%  | 会话管理+测试验证 |
| 数据加密 | 100%  | 服务端完整支持 |
| 客户端同步 | 50%  | 部分实现（有冲突解决，缺文件监控） |
| 文件共享 | 100% | 完整实现（权限管理+API接口） |
| 文件版本控制 | 100% | 完整实现（历史版本记录） |
| 冲突解决 | 100% | 客户端实现（4种策略） |


