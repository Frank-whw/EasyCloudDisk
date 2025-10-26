# SimpleCloudDrive
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
	- [ ] 使用云对象存储（Object Cloud Storage, 如AWS S3和OpenStack Swift）来存储用户数据
	- [ ] 业务逻辑部署在云虚拟机（如AWS EC2）
2. 基础服务与多用户支撑(包括所有计算机网络课的作业内容)
	1. 基础文件操作(上传、下载、列表、删除)
		- [x] 上传（单线程 多线程）
		- [ ] 下载
		- [ ] 列表
	2. 用户认证和多用户管理
		- [ ] 实现一个简单的用户注册/登录系统(不计入评分)
		- [ ] 使用Token(如JWT)进行API请求的身份验证(仅供参考)
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


