# 测试架构说明

## 测试结构

```
EasyCloudDisk/
├── test/                          # E2E 集成测试（Shell脚本）
│   ├── run_all_tests.sh          # 运行所有集成测试
│   ├── test_*.sh                 # 各种API集成测试脚本
│   └── README.md                 # 集成测试文档
│
├── server/
│   └── src/test/java/            # 服务器单元测试（Java）
│       ├── service/
│       │   ├── FileServiceTest.java
│       │   ├── UserServiceTest.java
│       │   └── ChunkServiceTest.java
│       └── controller/
│           └── FileControllerTest.java
│
└── client/
    └── src/test/java/            # 客户端单元测试（Java）
        ├── http/
        │   ├── AuthApiClientTest.java
        │   └── FileApiClientTest.java
        └── sync/
            └── SyncManagerTest.java
```

## 测试类型

### 1. 单元测试（Unit Tests）

**位置**: `server/src/test/java/` 和 `client/src/test/java/`

**特点**:
- 测试单个类/方法的功能
- 使用 Mock 隔离依赖
- 执行速度快（毫秒级）
- 不需要启动完整的应用

**Server端单元测试**:

| 测试类 | 测试目标 | 关键测试点 |
|--------|---------|-----------|
| `FileServiceTest` | FileService | 文件上传、下载、删除逻辑；异常处理；版本管理 |
| `UserServiceTest` | UserService | 用户注册、登录；密码加密；JWT生成 |
| `ChunkServiceTest` | ChunkService | 块级去重；分块存储；文件重组；引用计数 |
| `FileControllerTest` | FileController | HTTP请求处理；参数验证；响应格式 |

**Client端单元测试**:

| 测试类 | 测试目标 | 关键测试点 |
|--------|---------|-----------|
| `AuthApiClientTest` | AuthApiClient | API封装；错误处理；重试逻辑 |
| `FileApiClientTest` | FileApiClient | 文件API调用；Token管理；响应解析 |
| `SyncManagerTest` | SyncManager | 同步逻辑；文件事件处理；状态管理 |

### 2. 集成测试（Integration Tests）

**位置**: `test/` 目录

**特点**:
- 测试完整的API流程
- 需要启动真实的服务器
- 需要真实的数据库和S3连接
- 执行速度较慢（秒级）

**集成测试脚本**:
- `test_health.sh` - 健康检查
- `test_auth_register.sh` - 用户注册
- `test_auth_login.sh` - 用户登录
- `test_file_upload.sh` - 文件上传
- `test_file_list.sh` - 文件列表
- `test_file_download.sh` - 文件下载
- `test_file_delete.sh` - 文件删除
- `test_quick_upload.sh` - 秒传功能
- `test_chunk_deduplication.sh` - 块级去重
- `test_resumable_upload.sh` - 断点续传
- `test_resumable_sessions.sh` - 会话列表
- `test_delta_sync.sh` - 差分同步
- `test_encryption.sh` - 数据加密

## 运行测试

### 运行Server端单元测试

```bash
cd server
mvn test
```

运行特定测试类：
```bash
mvn test -Dtest=FileServiceTest
```

### 运行Client端单元测试

```bash
cd client
mvn test
```

### 运行集成测试

```bash
cd test
./run_all_tests.sh
```

运行单个集成测试：
```bash
./test_file_upload.sh
```

## 测试覆盖率目标

- **单元测试**: 70%+ 覆盖率
- **集成测试**: 覆盖所有主要API功能
- **端到端测试**: 覆盖关键用户场景

## 测试依赖

### Server端
- `spring-boot-starter-test` - Spring Boot测试支持
- `mockito-junit-jupiter` - Mockito Mock框架
- `spring-security-test` - Spring Security测试支持

### Client端
- `junit-jupiter` - JUnit 5测试框架
- `mockito-core` - Mockito Mock框架（需要手动添加）

## 编写测试的最佳实践

1. **测试命名**: 使用描述性的方法名，如 `testUpload_EmptyFile_ThrowsException`
2. **AAA模式**: Arrange（准备）→ Act（执行）→ Assert（断言）
3. **Mock外部依赖**: 数据库、网络、文件系统等都应Mock
4. **测试独立性**: 每个测试应该可以独立运行
5. **清晰的断言**: 使用有意义的错误消息

## 示例

### Server端单元测试示例

```java
@Test
void testUpload_Success_NewFile() {
    // Arrange（准备）
    when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
    when(fileRepository.findByUserIdAndDirectoryPathAndName(...))
        .thenReturn(Optional.empty());
    
    // Act（执行）
    FileMetadataDto result = fileService.upload(testFile, "/test", userId);
    
    // Assert（断言）
    assertNotNull(result);
    assertEquals("test.txt", result.getName());
    verify(chunkService, times(1)).storeFileInChunks(...);
}
```

### Client端单元测试示例

```java
@Test
void testLogin_Success() throws Exception {
    // Arrange
    when(httpClient.execute(any(HttpPost.class), any())).thenAnswer(...);
    
    // Act
    String token = authApiClient.login("test@example.com", "password");
    
    // Assert
    assertNotNull(token);
    verify(httpClient, atLeastOnce()).execute(any(), any());
}
```

## 注意事项

1. **单元测试不要依赖真实环境**: 数据库、S3、网络等都应Mock
2. **集成测试需要真实环境**: 但应该使用测试数据库和测试S3 bucket
3. **测试数据清理**: 集成测试后应清理测试数据
4. **测试隔离**: 每个测试应该使用独立的测试数据
5. **持续集成**: 所有测试应该在CI/CD中自动运行

