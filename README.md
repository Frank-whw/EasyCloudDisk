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

### 客户端同步建议（参考）
- 监听本地同步目录事件（创建/修改/删除/重命名）并调用上述API。
- 大文件增量同步可采用滚动哈希；断点续传建议基于分块与服务端`/files/upload`扩展。

### 错误代码定义
- `200 OK` 成功
- `400 EMAIL_EXISTS` 注册时邮箱已存在
- `401 INVALID_CREDENTIALS` 登录凭据错误或未认证访问
- `403 FORBIDDEN` 无权限访问资源
- `404 FILE_NOT_FOUND` 文件不存在
- `409 DUPLICATE_FILE` 文件去重冲突
- `500 INTERNAL_ERROR` 服务器内部错误

## Lab要求--用java做一个简易网盘
1. 在云端部署应用后端
	- [x] 找到一个可用的云（可以自己在虚拟机上部署OpenStack，也可以用AWS学生版）
	- [x] 使用云对象存储（Object Cloud Storage, 如AWS S3和OpenStack Swift）来存储用户数据
	- [ ] 业务逻辑部署在云虚拟机（如AWS EC2）
2. 基础服务与多用户支撑(包括所有计算机网络课的作业内容)
	1. 基础文件操作(上传、下载、列表、删除)
		- [x] 上传（单线程 多线程）
		- [ ] 下载
		- [ ] 列表
	2. 用户认证和多用户管理
		- [x] 实现一个简单的用户注册/登录系统(不计入评分)
		- [x] 使用Token(如JWT)进行API请求的身份验证(仅供参考)
		- [ ] 每个用户只能访问自己名下的文件和目录
	3. 实现一个客户端，并实现本地与云端的自动同步
		- [ ] 利作系统接口，客户端在用户本地实现一个“同步文件夹”，“同步文件央”中的内容和云端自动同步
		- [ ] 客户端需要持续监控本地同步文件央的文件系统事件(创建、修改、删除、重命名)
		- [ ] 当到变更时，自动将变更同步到服务器
		- [ ] 服务器端若有变更(例如通过另一个客户端上传)，也应能通知或由客户端拉取，以同步到本地
3. 实现同步过程中的若干网络流量优化技术
	1. 教据压缩
		- [ ] 在上传文件前，客户端应对文件进行压缩(例如使用GZIP或Zlib算法)
		- [ ] 在下载文件后，客户端应对文件进行解压
		- [ ] 服务器端存储压缩后的数据、
	2. 文件级去重:
		- [ ] 计算文件的哈希值(如MD5或SHA-256)
		- [ ] 在上传前，先询问服务器该哈希值的文件是否已存在
		- [ ] 如果存在，则服务器只需在用户文件表中创建一个新的指针(硬链接)，而无需重复存储文件内容
	3. 块级去重(这个有点难):
		- [ ] 将大文件分割成固定大小或可变大小的数据块(例如4MB)
		- [ ] 计算每个数据块的哈希值
		- [ ] 只上传服务器端不存在的“新”数据块，并组装文件元数据
4. 实现同步过程中的若干网络流量优化技术
	1. 差分同步（增量同步）
		- [ ] 当监控到本地大文件被修改后，不应重新上传整个文件。
		- [ ] 使用rsync类似的算法或滚动哈希（如Rabin-Karp算法）找出文件中被修改的部分（差异块）。
		- [ ] 仅将这些差异块上传到服务器，服务器再根据差异信息和原文件组合出新版本的文件。
		- [ ] 这极大地优化了大文件频繁小改动的同步效率。
	2. 断点续传
	3. 数据加密
		- [ ] 注意加密、去重、差分同步之间的冲突与关系
5. 文件共享与协同
	- [ ] 文件版本控制： 保留文件的历史版本，支持回滚到任意版本。
	- [ ] 冲突解决策略： 当同一个文件在两端同时被修改时，提供手动或自动的冲突解决机制（例如，生成冲突文件）。
	- [ ] 共享与协作： 实现文件/文件夹的共享功能，并设置权限（只读、可写）。


