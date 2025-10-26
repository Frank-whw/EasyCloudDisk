# Server TODO

## 目录结构
```
server/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── clouddisk/
        │           └── server/
        │               ├── ServerApplication.java
        │               ├── config/
        │               │   └── SecurityConfig.java
        │               ├── controller/
        │               │   └── AuthController.java
        │               ├── dto/
        │               │   ├── ApiResponse.java
        │               │   ├── AuthRequest.java
        │               │   └── AuthResponse.java
        │               ├── entity/
        │               │   └── User.java
        │               ├── repository/
        │               │   └── UserRepository.java
        │               ├── security/
        │               │   ├── JwtAuthenticationFilter.java
        │               │   └── JwtTokenProvider.java
        │               └── service/
        │                   ├── CustomUserDetailsService.java
        │                   └── UserService.java
        └── resources/
            └── application.yml
```

> 预计后续需要补充的模块（待创建）
```
controller/FileController.java
controller/HealthController.java
service/FileService.java
service/StorageService.java
service/S3StorageService.java
service/FileSyncService.java
entity/FileEntity.java
entity/FileVersion.java
repository/FileRepository.java
repository/FileVersionRepository.java
storage/S3ClientFactory.java
exception/GlobalExceptionHandler.java
exception/ErrorCode.java
security/JwtAuthenticationEntryPoint.java
config/AwsConfig.java
config/OpenApiConfig.java
```

## 文件与待办

### pom.xml
- 添加 Spring Boot Web、Spring Data JPA、PostgreSQL 驱动、Spring Security、JWT (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) 依赖。
- 引入 `aws-java-sdk-s3` 或 `software.amazon.awssdk:s3`，用于对接对象存储。
- 配置 Lombok、Validation (`spring-boot-starter-validation`)、日志依赖。
- 配置 `maven-compiler-plugin` 设定 `java.version`，并启用 annotation processing。

### ServerApplication.java
- 维持启动日志，增加启动后检查（如数据库连接、S3 配置校验）。
- 补充 `CommandLineRunner`：在启动时创建默认存储桶（若不存在）。

### config/SecurityConfig.java
- 调整授权规则以符合 README：
  - `/files/**` 需要认证；
  - `/actuator/**` 或 `/health` 允许匿名健康检查。
- 注入 `JwtAuthenticationEntryPoint` 与 `AccessDeniedHandler`，统一错误响应。
- 在 `securityFilterChain` 中配置异常处理、`logout()`、`headers().frameOptions().disable()`（若需要 H2 控制台）。
- 补充 `CorsConfiguration` 支持凭据与配置化来源列表。

### controller/AuthController.java
- 根据 README 返回精确错误码（`EMAIL_EXISTS`, `INVALID_CREDENTIALS` 等），避免捕获 `Exception` 后丢失细节。
- `register`、`login` 方法：
  - 捕获业务自定义异常 `BusinessException` 并映射到对应响应。
  - 记录审计日志。
- 未来扩展：新增 `POST /auth/logout`、`POST /auth/refresh`。

### dto/ApiResponse.java
- 确认序列化字段名称符合接口文档（`success`, `message`, `code`, `data`）。
- 补充静态工厂：`error(String message, int code, Map<String, Object> details)`。
- 使用 `@JsonInclude(JsonInclude.Include.NON_NULL)` 排除空值。

### dto/AuthRequest.java
- 校验注解需国际化消息；考虑新增字段 `rememberMe`、`deviceId`。

### dto/AuthResponse.java
- 按 README 字段补充 `userId`、`email`、`token`。
- 可添加 `refreshToken` 支持长会话。

### entity/User.java
- 修正 `UserRepository` 主键泛型（当前 `JpaRepository<User, Long>` 应改为 `JpaRepository<User, String>`），需要同步修改。
- 增加 `@Column(name = "token_version")` 等字段支持 token 失效。
- 校验器确保 email 大小写统一存储。

### repository/UserRepository.java
- 修改接口签名泛型与主键类型。
- 添加：`Optional<User> findByEmailIgnoreCase(String email)`。
- 添加：`@Modifying @Query` 更新 `token_version`。

### security/JwtAuthenticationFilter.java
- 使用 `OncePerRequestFilter` 的 `logger` 需要定义（例如 `private static final Logger logger = LoggerFactory.getLogger(...)`）。
- 捕获异常时返回 401，而不是仅记录日志；可通过 `AuthenticationEntryPoint` 完成。
- 支持从 `Authorization` 以及自定义 header（如 `X-Auth-Token`）读取 token。

### security/JwtTokenProvider.java
- 在 `validateToken` 中显式捕获 `JwtException` 并记录原因。
- 提供 `generateToken(String userId, Map<String, Object> claims)` 支持自定义 payload。
- 新增 `generateRefreshToken(String userId)`。
- 提供 `resolveToken(HttpServletRequest request)` 供过滤器复用。

### service/CustomUserDetailsService.java
- 抛出自定义异常 `UserNotFoundException`。
- 实现缓存（如 Spring Cache）以减少重复数据库查询。

### service/UserService.java
- 将 `Exception` 换成业务异常：`EmailExistsException`, `AuthenticationFailedException`。
- 在 `register` 中，返回 `ApiResponse` 时包含 `createdAt`。
- `login` 应该支持登陆失败次数限制、记录最后登录时间。
- 新增方法：
  - `public AuthResponse refreshToken(String refreshToken)`。
  - `public void logout(String userId)`。
  - `public Optional<User> getCurrentUser()`（结合 `SecurityContextHolder`）。

### resources/application.yml
- 从环境变量读取数据库、S3 配置，提供样例 `.env`。
- 增加 `springdoc`/`knife4j` 配置以生成接口文档。
- 提供 `logging` 输出文件路径（当前缺少换行需修复）。
- 增加 `management.endpoints.web.exposure.include=health,info`。

### 待创建：文件服务模块
- `entity/FileEntity.java`
  - 字段：`fileId`, `userId`, `name`, `filePath`, `s3Key`, `fileSize`, `contentHash`, `createdAt`, `updatedAt`。
  - 生命周期回调维护时间戳。
- `repository/FileRepository.java`
  - 方法：`List<FileEntity> findAllByUserId(String userId)`、`Optional<FileEntity> findByFileIdAndUserId(String fileId, String userId)`、`Optional<FileEntity> findByContentHash(String hash)`。
- `service/FileService.java`
  - 方法：`List<FileMetadataDto> listFiles(String userId)`, `FileMetadataDto upload(MultipartFile file, String filePath, String userId)`, `Resource download(String fileId, String userId)`, `void delete(String fileId, String userId)`。
  - 内部处理哈希计算、重复文件检查、版本控制。
- `service/StorageService.java`
  - 抽象接口：`store(InputStream data, long size, String key)`, `InputStream load(String key)`, `void delete(String key)`。
- `service/S3StorageService.java`
  - 通过 AWS SDK 与 S3 交互，支持分片上传、断点续传。
- `controller/FileController.java`
  - 实现 README 中 `/files`、`/files/upload`、`/files/{fileId}/download`、`/files/{fileId}`。
  - 添加 `@PreAuthorize("hasAuthority('USER')")`（如果引入角色）。
  - 统一返回 `ApiResponse`。
- `service/FileSyncService.java`
  - 负责远端变更通知，可预留 WebSocket 或 SSE 接口。

### 待创建：异常处理
- `exception/BusinessException.java`：携带 `ErrorCode`。
- `exception/ErrorCode.java`：枚举 README 中列出的业务码。
- `exception/GlobalExceptionHandler.java`：使用 `@RestControllerAdvice` 统一返回 `ApiResponse.error`。

### 待创建：健康检查与监控
- `controller/HealthController.java`：`GET /health` 返回应用状态、S3、数据库连通性。
- 集成 `spring-boot-starter-actuator` 并暴露关键指标。

### 测试
- 编写 `AuthControllerTest`, `FileControllerTest` 使用 `@SpringBootTest` + `MockMvc`。
- 引入 `Testcontainers` 启动 PostgreSQL、Localstack S3 进行集成测试。

### 部署
- 编写 Dockerfile、docker-compose，支持本地联调。
- 提供 CI/CD 脚本（GitHub Actions）执行测试、构建镜像、部署到云主机。
