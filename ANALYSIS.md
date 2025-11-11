# Cloud 云盘系统实现分析报告

**生成日期**: 2025-11-11  
**目标**: 基于 `reference/filecloud` 实现真正的云盘系统（使用 AWS S3 + RDS）

---

## 1. 架构对比

### Reference (本地存储方案)
- **后端**: Go (Gin框架) + 本地文件系统
- **前端**: Vue.js 4.5.15 + Ant Design 1.x
- **存储**: 本地磁盘目录 (`cloud/`)
- **数据**: 内存存储（目录结构、上传会话）
- **认证**: 单用户硬编码 (config.toml)

### Cloud (云存储方案)
- **后端**: Java Spring Boot 3.2 + AWS S3 + PostgreSQL/H2
- **前端**: Vue.js (待适配)
- **存储**: AWS S3 对象存储
- **数据**: 关系型数据库 (users, files 表)
- **认证**: JWT 多用户系统

---

## 2. 功能对比矩阵

| 功能 | Reference 实现 | Cloud 当前状态 | 实现优先级 |
|------|---------------|---------------|-----------|
| **用户认证** | 单用户硬编码 | ? JWT 多用户 (已完成) | P0 - 已完成 |
| **用户注册/登录** | ? 不支持 | ? /auth/register, /auth/login | P0 - 已完成 |
| **单文件上传** | ? multipart 到本地 | ? multipart 到 S3 | P0 - 已完成 |
| **多文件上传** | ? 批量上传 | ?? 需前端支持 | P1 |
| **文件夹上传** | ? 递归上传 | ?? 需前端+后端支持 | P1 |
| **文件下载** | ? 本地读取 | ? 流式下载/预签名URL | P0 - 已完成 |
| **文件列表** | ? 树形结构 | ? 扁平列表 | P0 - 已完成 |
| **文件删除** | ? 引用计数 | ? 直接删除 | P0 - 已完成 |
| **文件移动/拷贝** | ? 支持 | ? 未实现 | P2 |
| **秒传（文件级去重）** | ? MD5检查 | ?? 代码已有但需测试 | P1 |
| **分片上传** | ? 4MB分片 | ? 未实现 | P1 |
| **断点续传** | ? 会话恢复 | ? 未实现 | P1 |
| **块级去重** | ? 不支持 | ? 未实现 | P3 |
| **差分同步** | ? 不支持 | ? 未实现 | P3 |
| **数据压缩** | ? 不支持 | ? 未实现 | P2 |
| **分享链接** | ? 链接+提取码 | ? 未实现 | P2 |
| **分享列表** | ? 管理页面 | ? 未实现 | P2 |
| **在线预览** | ? TODO | ? 未实现 | P3 |
| **文件版本控制** | ? 不支持 | ? 未实现 | P2 |
| **冲突解决** | ? 不支持 | ? 未实现 | P2 |
| **本地同步客户端** | ? 不支持 | ?? 骨架已有 | P1 |

**优先级说明**:
- P0: 核心功能，必须先完成
- P1: 重要功能，Lab要求
- P2: 增强功能，提升用户体验
- P3: 高级功能，加分项

---

## 3. 已实现功能详情

### 3.1 后端 (Cloud/server)

#### ? 用户认证模块
- **文件**: `user/controller/AuthController.java`, `user/service/UserService.java`
- **实现**:
  - `POST /auth/register`: 邮箱注册，密码 BCrypt 加密
  - `POST /auth/login`: 邮箱登录，返回 JWT token
  - JWT 验证过滤器 (`JwtAuthenticationFilter`)
  - 全局异常处理 (`GlobalExceptionHandler`)
- **状态**: ? 功能完整，需测试

#### ? 文件管理模块
- **文件**: `file/controller/FileController.java`, `file/service/FileService.java`
- **实现**:
  - `GET /files`: 获取用户文件列表
  - `POST /files/upload`: 单文件上传 (multipart) 到 S3
  - `GET /files/{id}/download`: 流式下载文件
  - `DELETE /files/{id}`: 删除文件及 S3 对象
  - SHA-256 哈希计算
  - 文件级去重检查 (基于 contentHash)
- **状态**: ? 核心功能完整，需完善去重逻辑

#### ? S3 集成
- **文件**: `storage/service/S3ServiceImpl.java`
- **实现**:
  - AWS SDK v2 集成
  - 上传/下载/删除操作
  - 流式下载支持
- **状态**: ? 基础功能完整

#### ?? 数据库
- **Entity**: `User.java`, `File.java`
- **配置**: H2 (开发) + PostgreSQL (生产)
- **问题**: User 实体的主键类型不一致 (String vs UUID)

### 3.2 客户端 (Cloud/client)

#### ?? 同步客户端 (Java)
- **文件**: `client/src/main/java/com/clouddisk/client/`
- **状态**: 
  - ? 项目结构完整 (23个源文件)
  - ? Maven 编译成功
  - ? 功能实现未完成 (需查看具体代码)

---

## 4. 待实现功能清单 (按优先级)

### 阶段 1: 核心功能完善 (P0-P1)

#### 1.1 修复已知问题
- [ ] 修复 User 实体主键类型不一致问题
- [ ] 完善文件去重逻辑 (409 DUPLICATE_FILE 响应)
- [ ] 添加引用计数删除策略 (避免误删共享 S3 对象)
- [ ] 配置 LocalStack/MinIO 本地测试环境
- [ ] 编写单元测试和集成测试

#### 1.2 分片上传与断点续传
- [ ] 设计上传会话表 (`upload_sessions`)
  ```sql
  CREATE TABLE upload_sessions (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    file_name VARCHAR(255),
    content_hash VARCHAR(64),
    total_size BIGINT,
    chunk_size INT DEFAULT 4194304, -- 4MB
    uploaded_chunks JSONB, -- [1,2,5,7]
    s3_upload_id VARCHAR(255), -- S3 multipart upload ID
    status VARCHAR(20), -- INITIATED, IN_PROGRESS, COMPLETED, FAILED
    created_at TIMESTAMP,
    expires_at TIMESTAMP
  );
  ```

- [ ] 实现 API 端点:
  ```
  POST /files/multipart/initiate
  请求: { "fileName": "large.mp4", "fileSize": 524288000, "contentHash": "sha256..." }
  响应: { "sessionId": "uuid", "uploadId": "s3-upload-id", "chunkSize": 4194304 }
  
  PUT /files/multipart/{sessionId}/part/{partNumber}
  请求: multipart chunk data
  响应: { "partNumber": 1, "etag": "..." }
  
  GET /files/multipart/{sessionId}/status
  响应: { "uploadedChunks": [1,2,3], "totalChunks": 100 }
  
  POST /files/multipart/{sessionId}/complete
  请求: { "parts": [{"partNumber": 1, "etag": "..."}] }
  响应: { "fileId": "uuid", "s3Key": "..." }
  ```

#### 1.3 客户端同步功能
- [ ] 文件系统监听 (WatchService)
- [ ] 本地文件哈希计算
- [ ] 与服务器 API 集成
- [ ] 单向同步 (本地 → 云端)
- [ ] 冲突检测与处理

#### 1.4 前端适配
- [ ] 对接认证 API
- [ ] 文件列表展示 (表格+图标)
- [ ] 拖拽上传 + 进度条
- [ ] 多选操作 (批量删除、下载)

### 阶段 2: 高级特性 (P2)

#### 2.1 文件共享
- [ ] 创建 `shares` 表
  ```sql
  CREATE TABLE shares (
    share_id VARCHAR(36) PRIMARY KEY,
    user_id UUID NOT NULL,
    file_ids JSONB, -- ["file-id-1", "file-id-2"]
    share_code VARCHAR(10), -- 提取码
    expires_at TIMESTAMP,
    access_count INT DEFAULT 0,
    max_access INT, -- 最大访问次数
    created_at TIMESTAMP
  );
  ```

- [ ] API:
  ```
  POST /files/share
  请求: { "fileIds": ["uuid1", "uuid2"], "expiresInHours": 72 }
  响应: { "shareLink": "http://xxx/share/ABC123", "shareCode": "xyz9" }
  
  GET /share/{shareId}
  请求: ?code=xyz9
  响应: 文件列表或下载
  ```

#### 2.2 文件版本控制
- [ ] 创建 `file_versions` 表
- [ ] 每次修改保存历史版本
- [ ] 支持版本列表和回滚

#### 2.3 数据压缩
- [ ] 客户端 GZIP 压缩
- [ ] 元数据标记 `is_compressed`
- [ ] 自动解压下载

### 阶段 3: 优化与部署 (P3)

#### 3.1 块级去重
- [ ] 创建 `chunks` 表 (chunk_hash, s3_key, size)
- [ ] 创建 `file_chunks` 关联表
- [ ] 实现分块上传逻辑

#### 3.2 差分同步
- [ ] 滚动哈希算法 (Rabin fingerprint)
- [ ] 差异块检测
- [ ] 增量传输

#### 3.3 AWS 部署
- [ ] Terraform/CDK IaC 脚本
- [ ] Docker 镜像构建
- [ ] ECS/EC2 部署
- [ ] CloudWatch 监控
- [ ] S3 预签名 URL 优化
- [ ] CloudFront CDN

---

## 5. 技术栈映射

| 组件 | Reference | Cloud | AWS 服务 |
|------|-----------|-------|---------|
| 后端语言 | Go | Java | - |
| Web框架 | Gin | Spring Boot | - |
| 对象存储 | 本地文件系统 | AWS SDK | **S3** |
| 数据库 | 内存 | JPA/Hibernate | **RDS PostgreSQL** |
| 认证 | 硬编码 | JWT | **Cognito (可选)** |
| 容器部署 | - | Docker | **ECS/Fargate** |
| 负载均衡 | - | - | **ALB** |
| 监控日志 | - | Logback | **CloudWatch** |
| CDN加速 | - | - | **CloudFront** |
| 消息队列 | - | - | **SQS (可选)** |

---

## 6. Reference 核心代码位置

```
reference/filecloud/back-go/service/
├── web_api_auth.go         # 认证 (硬编码)
├── web_api_file.go         # 文件操作 (移动/拷贝/删除)
├── web_api_upload.go       # 上传 (分片/秒传)
├── web_api_shared.go       # 分享链接
├── fileInfos.go            # 文件元数据管理
└── util.go                 # 工具函数 (MD5计算等)
```

**关键逻辑**:
1. **分片上传**: `web_api_upload.go` - 前端计算分片，后端组装
2. **秒传**: 检查 MD5 是否存在，存在则创建硬链接
3. **引用计数删除**: `fileInfos.go` - 删除时检查引用数
4. **分享**: `web_api_shared.go` - 生成短链+提取码

---

## 7. 下一步行动计划

### 本周 (第1周)
1. ? 生成此分析文档
2. [ ] 修复 Server 已知问题
3. [ ] 完善基础文件操作测试
4. [ ] 实现秒传完整逻辑
5. [ ] LocalStack 本地环境搭建

### 第2周
1. [ ] 实现分片上传 API
2. [ ] 实现断点续传
3. [ ] 客户端同步基础功能

### 第3周
1. [ ] 前端页面适配
2. [ ] 文件共享功能
3. [ ] 集成测试完善

### 第4周
1. [ ] AWS 部署准备
2. [ ] 性能优化
3. [ ] 文档与演示

---

## 8. 关键决策

### 8.1 使用 AWS 托管服务的优势
- ? **S3**: 无限扩展、高可用、原生支持 multipart upload
- ? **RDS**: 托管数据库、自动备份、多 AZ 部署
- ? **IAM**: 细粒度权限控制
- ? **CloudWatch**: 统一监控告警
- ?? **成本**: 需要控制 S3 存储和流量成本

### 8.2 预签名 URL vs 代理下载
- **预签名 URL** (推荐):
  - ? 减轻服务器带宽压力
  - ? S3 直连速度快
  - ?? URL 有效期管理
  
- **代理下载**:
  - ? 权限控制更精确
  - ? 统计下载次数
  - ? 服务器带宽成本高

**建议**: 小文件代理下载 (<10MB)，大文件使用预签名 URL

### 8.3 去重策略
- **文件级去重**: 必须实现 (Lab 要求)
- **块级去重**: 高级特性，可选实现
- **冲突**: 加密会导致相同文件哈希不同，需要在加密前计算原始哈希

---

## 9. 测试策略

### 单元测试
- UserService 注册/登录逻辑
- FileService 哈希计算/去重检查
- S3Service 上传/下载

### 集成测试
- 完整上传流程 (含秒传)
- 分片上传+断点续传
- 并发上传安全性
- 权限验证

### 性能测试
- 1000个文件列表查询 (<100ms)
- 100MB 文件上传速度
- 并发上传 (10 用户同时)

---

## 10. 参考资料

- [AWS S3 Multipart Upload](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html)
- [Spring Boot + S3 集成](https://docs.spring.io/spring-cloud-aws/docs/current/reference/html/index.html)
- [JWT 最佳实践](https://datatracker.ietf.org/doc/html/rfc8725)
- [Rsync 算法原理](https://rsync.samba.org/tech_report/)

---

**备注**: 本文档将随着项目进展持续更新。
