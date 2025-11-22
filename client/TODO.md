# Client TODO

## 目录结构
```
client/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── com/
                └── clouddisk/
                    └── client/
                        └── ClientApplication.java
```

> 预计后续需要新增的包结构（待创建）
```
com/clouddisk/client/config/
com/clouddisk/client/http/
com/clouddisk/client/model/
com/clouddisk/client/sync/
com/clouddisk/client/util/
```

## 文件与待办

### pom.xml
- 配置客户端所需的依赖：`spring-boot-starter`, `spring-boot-starter-webflux` 或 `spring-boot-starter-web`, `spring-boot-configuration-processor`。
- 引入 HTTP 客户端（如 `spring-webflux WebClient` 或 `apache httpclient`）和 JSON 序列化（如 `jackson-databind`）。
- 添加文件监听与压缩相关依赖：`watchservice`（JDK 自带即可）、`commons-compress` / `java.util.zip`。
- 预留与 JWT 解析相关依赖（如 `jjwt-api`）。

### src/main/java/com/clouddisk/client/ClientApplication.java
- 作为客户端启动入口，负责：
  - 加载配置（如同步目录、服务器地址、凭据缓存等）。
  - 初始化 HTTP 客户端、任务调度线程池、文件监听器等核心组件。
  - 捕获启动异常并打印可读日志。
- 需要补充的核心方法：
  - `private static ClientRuntimeContext initializeContext()`：组装配置、HTTP 客户端、任务调度器。
  - `private static void startSyncLoop(ClientRuntimeContext context)`：启动文件事件监听与定时全量校验。
  - `private static void registerShutdownHook(ClientRuntimeContext context)`：释放线程池、关闭网络连接。

### 待创建：config 包
- `ClientProperties.java`：使用 `@ConfigurationProperties` 读取本地配置（服务器地址、端口、同步目录、压缩策略等）。
- `TokenStore.java`：负责本地缓存 JWT（读取/写入、自动刷新）。

### 待创建：http 包
- `AuthApiClient.java`
  - 方法：`login(AuthRequest request)`, `register(AuthRequest request)`, `refreshToken(String refreshToken)`。
  - 负责调用服务器 `/auth/**` 接口并处理返回的 `ApiResponse`。
- `FileApiClient.java`
  - 方法：`listFiles()`, `uploadFile(FileUploadRequest request)`, `downloadFile(String fileId, Path target)`, `deleteFile(String fileId)`。
  - 处理 multipart 上传、二进制下载、错误码转换。
- `ApiResponseHandler.java`
  - 方法：`<T> T unwrap(ApiResponse<T> response)`，统一抛出对应异常。

### 待创建：model 包
- `AuthRequest.java`, `AuthResponse.java`, `ApiResponse.java`, `FileMetadata.java` 等 DTO，字段要与 README 的接口契约保持一致。
- `FileUploadRequest.java`: 包含 `Path localPath`, `String filePath`, `byte[] compressedPayload`, `String contentHash`。

### 待创建：sync 包
- `SyncManager.java`
  - 方法：`startWatching()`, `stopWatching()`, `handleLocalEvent(WatchEvent<Path> event)`, `synchronizeRemoteChanges()`。
  - 将本地文件系统事件转化为 API 调用，管理增量同步任务。
- `DirectoryWatcher.java`
  - 方法：`start()`, `stop()`, `setEventListener(Consumer<FileEvent>)`。
  - 封装 `WatchService` 注册和事件分发。
- `HashCalculator.java`
  - 方法：`String computeFileHash(Path path)`, `Map<Integer, String> chunkHashes(Path path)` 支持文件级/块级去重。
- `CompressionService.java`
  - 方法：`byte[] compress(Path source)`, `void decompress(byte[] payload, Path target)`。
- `ConflictResolver.java`
  - 方法：`Path resolve(Path local, FileMetadata remote)`，负责冲突命名策略。

### 待创建：util 包
- `RetryTemplate.java`：对网络请求提供重试与指数退避。
- `LoggerFactory.java`：统一日志格式，或引入 SLF4J。
- `FileUtils.java`：处理跨平台路径、临时文件、磁盘空间检查。

### 其他待办
- 编写单元测试与集成测试（如使用 `JUnit` + `WireMock`）。
- 提供命令行参数或 GUI（如果需要）选择同步目录。
- 编写 README 用于指导客户端的打包与运行。
