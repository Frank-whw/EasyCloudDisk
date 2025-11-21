## EasyCloudDisk 实验报告

**小组成员:**

10235501437    贾馨雨   ；10235501413    张欣扬 ； 10213903418     韩悦 ；

10235501462    马舒童  ； 10235501471     詹江叶煜 

**项目仓库**：

https://github.com/Frank-whw/EasyCloudDisk/tree/hy

https://github.com/Frank-whw/EasyCloudDisk/tree/zjyy

### 一、项目概览

EasyCloudDisk 是一个基于 Spring Boot 后端框架与 AWS S3 云存储服务构建云存储系统。系统实现了完整的多用户文件管理生态，包括用户认证授权、文件基础操作（上传、下载、删除）、高级文件传输功能（秒传、断点续传、差分同步）以及实时文件变更通知等能力。采用前后端分离的架构：后端负责业务逻辑处理、用户认证、安全管控以及与 AWS S3 的交互；前端提供直观流畅的用户界面，通过封装的 API 客户端与后端进行通信。

### 二、目录结构与模块

- `server/` 后端服务
  - `controller/` 对外接口（认证、文件、协作、健康）
  - `service/` 业务实现（文件、上传、断点续传、差分同步、加密、用户、同步）
  - `security/` JWT 认证过滤链、异常处理、用户主体与安全配置
  - `entity/`、`repository/` 数据实体与持久化
  - `storage/` S3 存储实现（上传/下载/删除/存在性/建桶/健康）
  - `config/` Spring/AWS/OpenAPI/CORS 等配置
  - `resources/application.yml` 端口、数据源、JPA、JWT、CORS、AWS 配置
- `frontend/` 前端静态页面与脚本
  - `index.html` 页面结构与 UI
  - `css/` 样式
  - `js/` 模块
    - `config.js` 全局配置（API 地址、上传阈值、存储键名）
    - `api.js` 请求封装（认证、文件、秒传、断点续传、加密、健康）
    - `auth.js` 登录/注册/登出与页面切换
    - `fileManager.js` 文件列表、目录管理、视图切换与刷新
    - `uploadManager.js` 普通上传、秒传、断点续传与进度管理
    - `syncManager.js` SSE 订阅与变更通知（与后端 `/files/sync` 对接）
    - `hashCalculator.js` 文件哈希计算
    - `utils.js` 通用工具（提示、加载态、格式化等）
  - 启动脚本：`start.bat`（Windows）与 `start.sh`（Linux/WSL）
- `client/` Java 客户端 SDK 与同步组件（HTTP 客户端、目录监听、哈希计算、冲突处理、同步管理）
- `sync_client/` Python 同步示例脚本（简化同步流程）
- `scripts/` 部署相关脚本（自动部署、服务器启动、SSH 初始化）
- `test/` 接口联调脚本（认证、上传下载、断点续传、加密、协作等）
- 接口清单
  - 认证：`/auth/register`、`/auth/login`、`/auth/refresh`、`/auth/logout`
  - 文件：`/files`（列表）、`/files/upload`、`/files/{id}/download`、`/files/{id}`（删除）、`/files/directories`
  - 秒传：`/files/quick-check`、`/files/quick-upload`、`/files/check?contentHash=...`
  - 断点续传：`/files/resumable/init`、`/files/resumable/{sessionId}/chunk/{chunkIndex}`、`/files/resumable/{sessionId}/complete`、`/files/resumable/sessions`
  - 差分：`/files/{id}/signatures`、`/files/{id}/delta`
  - 加密：`/files/upload-encrypted`、`/files/{id}/encryption`、`/files/convergent-check`
  - 健康：`/health`

### 三、已实现功能

##### 1 . 在云端部署应用后端

- 找到一个可用的云

  **云平台部署**

  - 云平台与组件：选择 AWS 部署，后端运行在 EC2（Ubuntu LTS），对象存储使用 S3。
  - 部署目标：后端业务逻辑运行于云虚拟机（EC2），用户数据存储于云对象存储（S3）。

- 使用云对象存储来存储用户数据

  **S3服务实现:**

  - 核心接口 ： S3Service.java 定义了基本的文件操作方法（上传、下载、流式下载、删除）
  - 具体实现 ： S3ServiceImpl.java 提供了与AWS S3交互的完整逻辑
  - 主要功能 ：
    - 文件上传时自动处理内容类型，设置适当的元数据
    - 提供字节数组和流式下载两种下载方式，适应不同场景
    - 实现文件删除功能，支持资源清理

  S3客户端配置

  - 配置类 ： S3Config.java 负责创建和配置S3Client实例 

- 业务逻辑部署在云虚拟机

  **部署环境**

  - 服务器环境 ：在EC2虚拟机上运行Spring Boot应用
  - 运行模式 ：通过JAR包形式部署，使用 nohup 保持后台运行
  - 数据库 ：生产环境使用PostgreSQL（application-prod.yml中配置）

  **部署步骤（EC2 + S3）**

- 创建 S3 存储桶

  - 在 S3 创建 `clouddisk-<your-bucket>`；选择区域与访问策略

- 准备 IAM 策略与角色

  - 为 EC2 创建 IAM Role，授予 S3 的最小权限（list/get/put/delete 对指定桶）
  - 将该 Role 绑定到后端 EC2 实例，避免使用明文 Access Key

- 启动 EC2 实例

  - 选择 Ubuntu LTS 映像；开放 `TCP 22/80/443/8080`；分配公有 IP。
  - 登录后安装 JDK 21 与 Maven；拉取或同步项目代码到实例。

- 服务配置与启动

  - 设置环境变量：`AWS_REGION`、`AWS_S3_BUCKET`（若未使用 IAM Role，则另设 `AWS_ACCESS_KEY_ID`、`AWS_SECRET_ACCESS_KEY`）。
  - 在 `server/` 运行：`mvn spring-boot:run -DskipTests`；或打包为 JAR 并用 `scripts/server-startup.sh` 后台运行。

- 验证与联通

  - 打开健康检查 `http://<EC2-IP>:8080/health`；配置前端 `API_BASE_URL` 指向该地址。

##### 2 . 基础服务与多用户支撑

* 总览
  - 服务器 (`server/`) 提供完整的文件 CRUD、目录管理、版本控制、块级去重/压缩、SSE 通知等能力。
  - 安全层采用 Spring Security + JWT，无状态认证、细粒度权限校验以及共享控制。
  - Java 客户端 (`client/`) 与 Python 参考实现 (`sync_client/`) 共同展示了本地“同步文件夹”监听、去重上传、SSE 监听和远程拉取的整体链路。

- 基础文件操作（上传、下载、列表、删除、创建目录）

  - `FileController` 暴露上传、下载、列表、删除、创建目录、版本恢复、断点续传、秒传、差分同步等 API，并在每次写操作后调用 `FileSyncService.notifyChange` 推送给对应用户，实现即时同步。  

  ```53:199:server/src/main/java/com/clouddisk/controller/FileController.java
  @GetMapping public ResponseEntity<ApiResponse<List<FileMetadataDto>>> listFiles(...)
  @PostMapping("/upload") public ResponseEntity<ApiResponse<FileMetadataDto>> upload(...)
  @GetMapping("/{fileId}/download") public ResponseEntity<Resource> download(...)
  @DeleteMapping("/{fileId}") public ResponseEntity<ApiResponse<Void>> delete(...)
  @PostMapping("/directories") public ResponseEntity<ApiResponse<FileMetadataDto>> createDirectory(...)
  ```

  - `FileService` 负责实际业务：按用户隔离查询 (`findAllByUserId`)、标准化路径、写入块存储、版本归档、目录判重、删除时处理引用计数等，确保所有 CRUD 操作与版本/块服务联动。  

  ```66:228:server/src/main/java/com/clouddisk/service/FileService.java
  public List<FileMetadataDto> listFiles(String userId, String path) { ... fileRepository.findAllByUserId(userId) ... }
  public FileMetadataDto upload(...) { ... chunkService.storeFileInChunks(...); fileVersionRepository.save(latest); }
  public ResponseEntity<Resource> download(...) { stream = chunkService.assembleFile(...); }
  public void delete(...) { chunkService.deleteFileChunks(file.getFileId()); ... }
  public FileMetadataDto createDirectory(...) { ... fileRepository.save(entity); }
  ```

- 用户认证和多用户管理

  - **用户注册/登录系统 **:
  - `AuthController` + `UserService` 实现注册/登录/刷新/注销，登录后返回 access token + refresh token；注册阶段校验邮箱唯一，登录时委托 `AuthenticationManager` 并使用 BCrypt 保存密码。  

  ```37:74:server/src/main/java/com/clouddisk/controller/AuthController.java
  @PostMapping("/register") public ResponseEntity<ApiResponse<AuthResponse>> register(...)
  @PostMapping("/login") public ResponseEntity<ApiResponse<AuthResponse>> login(...)
  @PostMapping("/refresh") public ResponseEntity<ApiResponse<AuthResponse>> refresh(...)
  @PostMapping("/logout") public ResponseEntity<ApiResponse<Void>> logout()
  ```

  ```52:133:server/src/main/java/com/clouddisk/service/UserService.java
  public AuthResponse register(RegisterRequest request) { ... passwordEncoder.encode(...) ... }
  public AuthResponse login(AuthRequest request) { authenticationManager.authenticate(...); }
  public AuthResponse refreshToken(String refreshToken) { tokenProvider.validateToken(...) ... }
  ```

  - **使用Token（如JWT）进行API请求的身份验证:**
  - `SecurityConfig` 将 `/auth/**` 设为匿名入口，其它 `/files/**` 必须携带 `Authorization: Bearer <JWT>`；HTTP 会话全部无状态，JWT 过滤器负责解析令牌并注入 `UserPrincipal`。  

  ```58:126:server/src/main/java/com/clouddisk/config/SecurityConfig.java
  http.cors().csrf().disable()
  .sessionManagement(SessionCreationPolicy.STATELESS)
  .authorizeHttpRequests().requestMatchers("/auth/**").permitAll().requestMatchers("/files/**").authenticated()
  http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
  ```

  - **每个用户只能访问自己名下的文件和目录**:
  - `FileAccessService` 在每个文件操作前校验请求者是否为所有者或匹配共享权限，结合 `FileShareRepository` 控制访问范围，确保“每个用户只能看到和操作自己的文件/目录”。  

  ```33:120:server/src/main/java/com/clouddisk/service/FileAccessService.java
  public FileAccessContext requireAccess(String fileId, String requesterId, FilePermission requiredPermission) {
      FileEntity file = fileRepository.findById(fileId)...;
      if (file.getUserId().equals(requesterId)) return FileAccessContext.owner(file);
      List<FileShare> shares = fileShareRepository.findAllBySubjectId(requesterId);
      ...
      if (matched == null || !matched.getPermission().allows(requiredPermission)) {
          throw new BusinessException(ErrorCode.PERMISSION_DENIED, "缺少访问该文件的权限");
      }
  }
  ```

- 实现一个客户端，并实现本地与云端的自动同步

  - **利用操作系统接口，客户端在用户本地实现一个“同步文件夹”，“同步文件夹”中的内容和云端自动同步**:
  - Java 客户端中的 `DirectoryWatcher` 封装 `WatchService` 监听同步目录的创建/修改/删除事件，并通过回调推送给 `SyncManager`。  

  ```38:161:client/src/main/java/com/clouddisk/client/sync/DirectoryWatcher.java
  watchService = FileSystems.getDefault().newWatchService();
  watchDir.register(... ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
  eventListener.accept(new FileEvent(eventType, filePath, pathEvent));
  ```

  - `SyncManager` 以 `DirectoryWatcher` 为本地入口，结合 `HashCalculator` + `CompressionService` 在检测到 CREATE/MODIFY 时先算哈希、再压缩并通过 `FileApiClient` 执行秒传检测、S3 直传或 API 上传；线程池异步执行，预留 `synchronizeRemoteChanges()` 与冲突解决逻辑。  

  ```95:269:client/src/main/java/com/clouddisk/client/sync/SyncManager.java
  directoryWatcher.setEventListener(this::handleLocalEvent);
  handleLocalEvent -> handleFileUpload -> hashCalculator.computeFileHash(...) + compressionService.compress(...);
  fileApiClient.checkFileExists(contentHash)... s3Service.uploadFile(...) ... fileApiClient.uploadFile(request);
  ```

  - **客户端需要持续监控本地同步文件夹的文件系统事件（创建、修改、删除、重命名）:**
  - `FileApiClient` 实现基础 REST 调用：`/files` 列表、`/files/upload` 上传、`/files/{id}/download` 下载、`/files/{id}` 删除，以及去重查询、上传完成通知等，所有请求会自动附加 JWT。  

  ```59:309:client/src/main/java/com/clouddisk/client/http/FileApiClient.java
  public List<FileResponse> listFiles() { HttpGet /files ... }
  public boolean uploadFile(FileUploadRequest request) { HttpPost /files/upload ... }
  public boolean downloadFile(String fileId, Path target) { HttpGet /files/{fileId}/download ... }
  public boolean deleteFile(String fileId) { HttpDelete /files/{fileId} ... }
  public boolean checkFileExists(String contentHash) { HttpGet /files/check?... }
  ```

  - **当检测到变更时，自动将变更同步到服务器:**
  - Python `sync_client` 通过 `watchdog` 监听本地目录，将 event 翻译成上传/建目录请求；并使用 `sseclient` 订阅 `/files/sync` SSE 流，收取其他客户端带来的远程变更，展示完整的端到端自动同步示例。  

  ```29:215:sync_client/sync_client.py
  response = requests.post("/auth/login"...); token = data.get("token")
  observer.schedule(SyncHandler, SYNC_DIR, recursive=True)
  def listen_server_events(...):
      response = requests.get("/files/sync", stream=True, headers=headers)
      client = sseclient.SSEClient(response)
      for event in client.events(): logger.info(f"收到服务器通知: {data}")
  ```

  - **服务器端若有变更（当通过另一个客户端上传），也应能通知或由客户端拉取，以同步到本地**:
  - 服务端的 SSE 推送由 `FileSyncService` 统一维护，注册时即发送 “connected” 事件，写操作后 `notifyChange` 向指定用户广播 `file-change`，供客户端同步。  

  ```26:52:server/src/main/java/com/clouddisk/service/FileSyncService.java
  public SseEmitter register(String userId) { emitters.put(userId, emitter); emitter.send(event().name("connected")...); }
  public void notifyChange(String userId, Object payload) {
      SseEmitter emitter = emitters.get(userId);
      emitter.send(SseEmitter.event().name("file-change").data(payload));
  }
  ```

  * 客户端同步策略
    * **本地→云端**：监听文件夹 → 算哈希/压缩 → 秒传检测 → 上传或跳过 → 服务端写入块存储并发 SSE。
    * **云端→本地**：客户端长连 SSE（或轮询 `/files` 比对）→ 接到 `file-change` 后触发 `synchronizeRemoteChanges()` 下载差异。
    * **跨端一致性**：服务器端的多版本控制、块级去重、冲突检测（`expectedVersion`）与 `FileAccessService` 的权限裁决共同保障同一账号/共享用户的协作体验。

##### 3 . 实现同步过程中的若干网络流量优化技术

- 数据压缩

  - **在上传文件前，客户端应对文件进行压缩**:
  - **上传端策略**：服务端在 `StorageService.storeFile`/`storeBytes` 中根据 `compress` 标志对原始字节进行 GZIP 压缩，并在 S3 元数据里记录 `original-size`、`compressed`、`sha256` 等字段，保证上传负载只包含压缩后的 payload。  

  ```46:84:server/src/main/java/com/clouddisk/storage/S3StorageService.java
  public String storeFile(..., boolean compress) {
      ...
      if (compress) {
          try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(bos)) {
              gzip.write(bytes);
          }
          payload = bos.toByteArray();
          requestBuilder.contentEncoding("gzip");
      }
      s3Client.putObject(... payload);
  }
  ```

  - **在下载文件后，客户端应对文件进行解压**:
  - **下载端策略**：服务端在读文件或重组块时，根据存储的 `compressed` 标志使用 `GZIPInputStream` 自动解压；客户端只需按常规下载接口读取即可。  

  ```132:147:server/src/main/java/com/clouddisk/storage/S3StorageService.java
  public InputStream loadFile(String storageKey, boolean decompress) {
      ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
      if (decompress) {
          return new java.util.zip.GZIPInputStream(response);
      }
      return response;
  }
  ```

  - **服务器端存储压缩后的数据:**
  - **客户端要求**：大文件在上行前使用 GZIP/Zlib 预压缩，若压缩率过小可关闭 `compress` 避免放大；下行后若响应 `Content-Encoding: gzip`，需自动解包。

- 文件级去重

  - **计算文件的哈希值:**
  - **哈希探测**：客户端对全文件计算 SHA-256，使用 `/files/convergent-check` 或 `/files/quick-upload` 前的 `checkQuickUpload` 判断是否已有相同内容，避免重复上传。  

  ```54:95:server/src/main/java/com/clouddisk/service/AdvancedUploadService.java
  public boolean checkQuickUpload(String hash, String userId) {
      return fileRepository.findAll().stream()
              .anyMatch(file -> hash.equals(file.getContentHash()) && !file.isDirectory());
  }
  @Transactional
  public FileMetadataDto quickUpload(String hash, String fileName, String path, String userId) {
      FileEntity sourceFile = fileRepository.findAll().stream()
              .filter(file -> hash.equals(file.getContentHash()) && !file.isDirectory())
              .findFirst()
              .orElseThrow(...);
      FileEntity newFile = new FileEntity();
      newFile.setStorageKey(sourceFile.getStorageKey());
      ...
  }
  ```

  - **在上传前，先询问服务器该哈希值的文件是否已存在:**

  - **存储复用**: 若服务器已有相同哈希，`quickUpload` 只复制元数据，`storageKey` 指向同一物理对象，创建一个新的指针（硬链接），而无需重复存储文件内容,无需再写入数据，网络消耗为 0。  

    - 先通过 `contentHash` 找到一个已有的 `FileEntity`（`sourceFile`），也就是已经存在于对象存储中的那份文件。  
    - 新建 `FileEntity newFile` 时直接把 `storageKey`、`fileSize`、`contentHash` 从 `sourceFile` 拷贝过来，完全没有写入块或调用 `ChunkService`。  
    - 这样数据库里只是新增一条文件元数据记录，`storageKey` 仍指向旧的物理对象，所以网络、磁盘都不需要再次写入。  


  ```68:96:server/src/main/java/com/clouddisk/service/AdvancedUploadService.java
  FileEntity sourceFile = fileRepository.findAll().stream()
          .filter(file -> hash.equals(file.getContentHash()) && !file.isDirectory())
          .findFirst().orElseThrow(...);
  
  FileEntity newFile = new FileEntity();
  newFile.setStorageKey(sourceFile.getStorageKey()); // 复用已有对象
  newFile.setFileSize(sourceFile.getFileSize());
  newFile.setContentHash(sourceFile.getContentHash());
  fileRepository.save(newFile);
  ```

  - 因为整个流程只涉及数据库查询/写入和日志，缺少任何对存储服务的上传调用，秒传时网络开销为 0、存储复用同一个 `storageKey`。
  - **客户端要求**: 上传前必须计算哈希并调用探测接口；若命中，直接发送“秒传”请求，服务器会在用户目录下创建逻辑引用。

* 块级去重

  * **将大文件分割成固定大小或可变大小的数据块、计算每个数据块的哈希值:**
  * **固定块切分**：`ChunkService` 将文件按 4 MB 分块并计算每块 SHA-256；同时根据 `compress` 决策对块进行 GZIP 压缩。  

  ```28:94:server/src/main/java/com/clouddisk/service/ChunkService.java
  public static final int CHUNK_SIZE = 4 * 1024 * 1024;
  public int storeFileInChunks(...) {
      List<byte[]> chunks = splitIntoChunks(fileData);
      for (...) {
          String chunkHash = DigestUtils.sha256Hex(chunkData);
          FileChunk chunk = chunkRepository.findByChunkHash(chunkHash)
                  .orElseGet(() -> uploadNewChunk(chunkHash, chunkData, userId, compress));
          chunk.incrementRef();
          mappingRepository.save(mapping);
      }
  }
  ```

  - **只上传服务器端不存在的“新”数据块，并组装文件元数据：**
  - **仅上传新块**：`uploadNewChunk` 在 GZIP 后如果发现压缩反而变大，会回退到原始数据，保证传输最优；并把 `chunkHash` 映射到 S3 中的唯一对象，引用计数支撑多文件共享。  

  ```224:265:server/src/main/java/com/clouddisk/service/ChunkService.java
  private FileChunk uploadNewChunk(...) {
      if (compress) {
          try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
              gzip.write(chunkData);
          }
          if (dataToUpload.length >= chunkData.length) {
              dataToUpload = chunkData;
              actuallyCompressed = false;
          }
      }
      String storageKey = storageService.storeBytes(dataToUpload, keyPrefix, filename, actuallyCompressed);
      chunk.setCompressed(actuallyCompressed);
      chunk.setRefCount(1);
      return chunkRepository.save(chunk);
  }
  ```

  - **客户端协作**：大文件上传流程需先在本地切块计算哈希，先询问服务器哪些块不存在，仅对缺失块调用上传接口（可通过差分同步或断点续传 API 传输），最后提交块列表让服务器组装文件版本。

##### 4 .实现同步过程中的若干网络流量优化技术

* **差分同步（增量同步）:**

  * **当监控到本地大文件被修改后，不应重新上传整个文件。**
  * `FileController` 暴露 `/files/{id}/signatures` 与 `/files/{id}/delta`，客户端先获取最新块签名，再上传需要替换的块；控制器内在 `applyDelta` 后触发 `fileSyncService.notifyChange`，确保其他客户端获知是一次增量刷新。  

  ```255:282:server/src/main/java/com/clouddisk/controller/FileController.java
  @GetMapping("/{fileId}/signatures") … diffSyncService.getFileSignatures(...)
  @PostMapping("/{fileId}/delta") … diffSyncService.applyDelta(...); fileSyncService.notifyChange(... "delta-update")
  ```

  - **使用rsync类似的算法或滚动哈希（如Rabin-Karp算法）找出文件中被修改的部分（差异块）。**
  - `DiffSyncService` 以 4 KB 滚动窗口生成签名，并在 `applyDelta` 阶段仅替换被标记的块，其余块通过 `ChunkService` 查表重用，同时利用 `storageService` 直接读取旧块内容避免回传；最后重新计算 `sha256` 并通过 `chunkService.storeFileInChunks(..., true)` 走块级去重。  

  ```35:199:server/src/main/java/com/clouddisk/service/DiffSyncService.java
  private static final int ROLLING_WINDOW_SIZE = 4096;
  sig.put("chunkIndex"... "hash"... "offset"...);
  if (deltaChunks.containsKey(...)) 使用新块，否则复用旧块并 storageService.loadFile(...)
  chunkService.storeFileInChunks(fileId, file.getVersion(), finalData, userId, true);
  ```

  - **仅将这些差异块上传到服务器，服务器再根据差异信息和原文件组合出新版本的文件。**
  - 块级 dedupe 由 `ChunkService` 保证：每个 4 MB 块计算 `sha256`，若哈希已存在则递增引用计数并跳过上传，从根本上削减网络流量；差分同步生成的最终文件仍按同样机制拆块，实现“增量 + 去重”的双层优化。  

  ```28:120:server/src/main/java/com/clouddisk/service/ChunkService.java
  public static final int CHUNK_SIZE = 4 * 1024 * 1024;
  FileChunk chunk = chunkRepository.findByChunkHash(chunkHash)
          .orElseGet(() -> uploadNewChunk(...));
  chunk.incrementRef(); mappingRepository.save(... sequenceNumber/offset);
  ```

* 断点续传

  - 断点续传通过 `AdvancedUploadService` 管理上传会话：`/files/resumable/init` 根据文件大小计算分块数（默认 2 MB）并生成 `sessionId`；`/chunk/{index}` 逐块写入；`/complete` 在确认全量块后合并。  

  ```201:254:server/src/main/java/com/clouddisk/controller/FileController.java
  @PostMapping("/resumable/init") … advancedUploadService.initResumableUpload(...)
  @PostMapping("/resumable/{sessionId}/chunk/{chunkIndex}") … uploadChunk(...)
  @PostMapping("/resumable/{sessionId}/complete") … completeResumableUpload(...)
  ```

  ```35:191:server/src/main/java/com/clouddisk/service/AdvancedUploadService.java
  private static final int CHUNK_SIZE = 2 * 1024 * 1024;
  initResumableUpload(...) -> totalChunks ceil(fileSize / CHUNK_SIZE)
  uploadChunk(...) 校验会话/索引后保存分块并记录 uploadedChunks
  completeResumableUpload(...) 检查分块覆盖率，再走标准上传逻辑
  ```

  - 结合 `UploadSession` 的状态字段与定时任务 `cleanExpiredSessions`，可在网络波动时随时重连继续上传

* 数据加密:注意加密、去重、差分同步之间的冲突与关系

  - 客户端加密文件通过 `/files/upload-encrypted` 入口，`EncryptionService.uploadEncryptedFile` 先复用 `fileService.upload` 完成物理写入，再记录算法、KDF、盐、迭代次数、IV、是否收敛加密等元数据；`/files/{id}/encryption` 返回这些字段供下载端解密。  

  ```284:330:server/src/main/java/com/clouddisk/controller/FileController.java
  @PostMapping("/upload-encrypted") … encryptionService.uploadEncryptedFile(...)
  @GetMapping("/{fileId}/encryption") … encryptionService.getEncryptionMetadata(...)
  @PostMapping("/convergent-check") … encryptionService.checkConvergentQuickUpload(...)
  ```

  ```42:147:server/src/main/java/com/clouddisk/service/EncryptionService.java
  uploadEncryptedFile(...) -> validate params → fileService.upload(...)
  encryptionMetadata.setAlgorithm/...setConvergent(...); repository.save(...)
  checkConvergentQuickUpload(...) 仅当 metadata.getConvergent() 且原始 hash 匹配时返回 true
  ```

  - 加密与去重/差分的冲突：块级 dedupe 和差分同步都依赖稳定的 plaintext 哈希（`DigestUtils.sha256Hex(chunk)`）；若客户端使用随机化的 AES-GCM，每次上传都会生成不同密文，导致去重和滚动哈希失效、网络量恢复到整文件传输。为此，系统支持 **收敛加密**（Convergent Encryption）：当 `EncryptedUploadRequest.convergent=true` 且客户端提供 `originalHash` 时，可用相同明文 derive 相同密钥/IV，使 chunk hash 仍可匹配，再配合 `checkConvergentQuickUpload` 走秒传或差分路径。

##### 5.文件共享与协同

- 文件版本控制： 保留文件的历史版本，支持回滚到任意版本。

  - `FileVersion` 记录每一次写入的 `fileId/versionNumber/storageKey/fileSize/contentHash/createdAt/updatedBy`，为历史列表与回滚提供结构化审计数据。  

  ```16:40:server/src/main/java/com/clouddisk/entity/FileVersion.java
      private String fileId;
      private int versionNumber;
      private String storageKey;
      private long fileSize;
      private String contentHash;
      private Instant createdAt;
      private String updatedBy;
  ```

  - `FileService.upload` 在新建时以 `version=1` 起步，覆盖时先 `archiveCurrentVersion` 落盘旧版本，然后写入块存储并保存最新版的 `FileVersion` 记录，保证每次修改都可追溯。  

  ```123:167:server/src/main/java/com/clouddisk/service/FileService.java
       boolean isNewFile = (entity == null);
       ensureExpectedVersion(expectedVersion, entity, isNewFile);
       if (isNewFile) { ... entity.setVersion(1); } else {
           archiveCurrentVersion(entity);
           entity.setVersion(entity.getVersion() + 1);
       }
       chunkService.storeFileInChunks(...);
       FileVersion latest = new FileVersion();
       latest.setFileId(entity.getFileId());
       latest.setVersionNumber(entity.getVersion());
       latest.setStorageKey("chunked");
       ...
  ```

  - 历史查询与回滚：`listVersions` 将 `file_versions` 转成 DTO；`restoreVersion` 克隆目标版本的块数据，生成新的 `version+1` 并再次写入 `FileVersion`，实现“回滚但不丢失历史”的不可变链。  

  ```307:360:server/src/main/java/com/clouddisk/service/FileService.java
      public List<FileVersionDto> listVersions(...) {
          return fileVersionRepository.findAllByFileIdOrderByVersionNumberDesc(fileId).stream()
                  .map(version -> { ... dto.setVersionNumber(...); });
      }
      public FileMetadataDto restoreVersion(...) {
          FileVersion target = fileVersionRepository.findByFileIdAndVersionNumber(...);
          int newVersion = entity.getVersion() + 1;
          chunkService.cloneVersion(fileId, versionNumber, newVersion);
          entity.setVersion(newVersion);
          ...
          fileVersionRepository.save(restored);
      }
  ```

  - `FileController` 公开 `/files/{id}/versions` 与 `/files/{id}/versions/{version}/restore`，所有写操作都支持 `expectedVersion` 参数，用统一 API 暴露版本感知接口。  

  ```69:146:server/src/main/java/com/clouddisk/controller/FileController.java
  @PostMapping("/upload") ... @RequestParam(value = "expectedVersion", required = false) Integer expectedVersion
  @PostMapping("/{fileId}/replace") ... expectedVersion
  @GetMapping("/{fileId}/versions") ... fileService.listVersions(...)
  @PostMapping("/{fileId}/versions/{versionNumber}/restore") ... expectedVersion
  ```

- 冲突解决策略： 当同一个文件在两端同时被修改时，提供手动或自动的冲突解决机制（例如，生成冲突文件）。

  - 服务端乐观锁由 `ensureExpectedVersion` 统一实现：上传、替换、回滚等入口若发现 `expectedVersion` 与当前版本不一致，直接抛出 `FILE_CONFLICT`，阻止误覆盖。  

  ```407:415:server/src/main/java/com/clouddisk/service/FileService.java
      private void ensureExpectedVersion(Integer expectedVersion, FileEntity entity, boolean isNewFile) {
          if (expectedVersion == null) return;
          int currentVersion = isNewFile ? 0 : entity.getVersion();
          if (!Objects.equals(expectedVersion, currentVersion)) {
              throw new BusinessException(ErrorCode.FILE_CONFLICT,
                      String.format("版本不一致：期望 %d，实际 %d", expectedVersion, currentVersion));
          }
      }
  ```

  - Java 同步客户端在检测到冲突时通过 `ConflictResolver` 选择策略：保留本地、覆盖远程、`KEEP_BOTH` 生成带时间戳的副本，或 `USE_NEWER` 按修改时间择优，提供自动化/半自动化冲突消解能力。  

  ```16:184:client/src/main/java/com/clouddisk/client/sync/ConflictResolver.java
      public enum ConflictResolutionStrategy { USE_LOCAL, USE_REMOTE, KEEP_BOTH, USE_NEWER }
      public Path resolve(Path local, FileMetadata remote, ConflictResolutionStrategy strategy) { ... }
      private Path resolveKeepBoth(Path local, FileMetadata remote) {
          String newName = generateUniqueFileName(fileName);
          return local.getParent().resolve(newName);
      }
  ```

- 共享与协作： 实现文件/文件夹的共享功能，并设置权限（只读、可写）。

  - `FilePermission` 仅定义 `READ` 与 `EDIT`，其中 `EDIT` 自动放行为 superset；`FileAccessService.requireAccess` 会先看是否为所有者，再在共享表里找可继承的记录并校验权限。  

  ```6:18:server/src/main/java/com/clouddisk/model/FilePermission.java
  public enum FilePermission { READ, EDIT;
      public boolean allows(FilePermission required) {
          if (this == EDIT) return true;
          return this == required;
      }
  }
  ```

  ```33:94:server/src/main/java/com/clouddisk/service/FileAccessService.java
      public FileAccessContext requireAccess(String fileId, String requesterId, FilePermission requiredPermission) {
          FileEntity file = fileRepository.findById(fileId)
                  .orElseThrow(...);
          if (file.getUserId().equals(requesterId)) return FileAccessContext.owner(file);
          List<FileShare> shares = fileShareRepository.findAllBySubjectId(requesterId);
          FileShare matched = findApplicableShare(file, shares);
          if (matched == null || !matched.getPermission().allows(requiredPermission)) {
              throw new BusinessException(ErrorCode.PERMISSION_DENIED, "缺少访问该文件的权限");
          }
          return FileAccessContext.shared(file, matched);
      }
  ```

  - 创建共享时仅文件拥有者可操作，系统会校验目标用户、禁止重复分享，并根据 `inheritChildren` 和目录属性决定是否允许子目录继承。共享内容浏览时做路径规范化与越界检查，避免“../”逃逸。  

  ```47:155:server/src/main/java/com/clouddisk/service/FileShareService.java
      public ShareInfoDto createShare(String ownerId, ShareCreateRequest request) {
          FileEntity file = fileRepository.findById(request.getFileId())
                  .orElseThrow(...);
          if (!file.getUserId().equals(ownerId)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
          User targetUser = userRepository.findByEmailIgnoreCase(...)
                  .orElseThrow(...);
          fileShareRepository.findByFileIdAndSubjectId(...).ifPresent(... SHARE_ALREADY_EXISTS ...);
          FileShare share = new FileShare();
          share.setPermission(request.getPermission());
          share.setInheritChildren(request.isInheritChildren() && file.isDirectory());
          share.setSharePath(fileAccessService.buildFullPath(file));
          fileShareRepository.save(share);
      }
      public List<FileMetadataDto> listSharedContents(String shareId, String subjectId, String relativePath) {
          if (StringUtils.hasText(relativePath) && !share.isInheritChildren()) {
              throw new BusinessException(ErrorCode.PERMISSION_DENIED, "该共享未授权访问子目录");
          }
          String targetPath = normalizePathInsideShare(basePath, relativePath);
          if (!targetPath.startsWith(basePath)) {
              throw new BusinessException(ErrorCode.ACCESS_DENIED, "路径越界");
          }
          return fileRepository.findAllByUserIdAndDirectoryPath(...).map(entity -> fileService.toDto(...));
      }
  ```

  - API 层通过 `ShareController` 提供创建共享、列出我分享的/能访问的、撤销共享以及基于 `shareId+path` 浏览目录等接口，所有入口均要求登录用户。  

  ```32:74:server/src/main/java/com/clouddisk/controller/ShareController.java
  @PostMapping ... createShare(...)
  @GetMapping("/owned") ... listOwnedShares(...)
  @GetMapping("/access") ... listAccessibleShares(...)
  @DeleteMapping("/{shareId}") ... revokeShare(...)
  @GetMapping("/{shareId}/contents") ... listSharedContents(...)
  ```

##### 6.前端搭建

- **认证体验**  
  - `auth.js` 负责登录/注册视图切换、本地校验（邮箱/密码规则）、调用后端 `/auth` API，并把 `token`、`refreshToken`、`userId` 缓存到 `localStorage`。登录成功后切换到主界面并启动文件同步。  

```18:158:frontend/js/auth.js
const response = await api.login(email, password);
localStorage.setItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN, response.data.token);
...
if (fileManager) { fileManager.loadFiles(); }
if (syncManager) { syncManager.start(); }
```

- **统一 API 访问层**  
  - `api.js` 封装 `fetch` 请求、自动附带 JWT Header、处理 401 退出和二进制下载；围绕文件管理扩展了 `listFiles`、`createDirectory`、`downloadFile`，同时暴露 `quickUpload`、断点续传、差分同步、收敛加密等高级接口，供各功能模块复用。  

```3:225:frontend/js/api.js
const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
if (!(options.body instanceof FormData)) headers['Content-Type'] = 'application/json';
async quickUpload(hash, fileName, path = null) { return this.post('/files/quick-upload', {...}); }
async initResumableUpload(...) { return this.post('/files/resumable/init', {...}); }
```

- **文件管理 UI/交互**  
  - `fileManager.js` 持有 `currentPath`、`viewMode` 状态，初始化拖拽上传和搜索，调用 `api.listFiles` 拉取目录；支持网格/列表渲染、面包屑导航、进入/返回目录、下载、删除和新建文件夹，并在空目录时提示拖拽上传。  

```10:399:frontend/js/fileManager.js
const response = await api.listFiles(targetPath); this.files = response.data || [];
container.innerHTML = '<div class="file-list-grid"></div>'; ... createFileItem(file, 'grid');
<button ... onclick="fileManager.downloadFile('${file.fileId}', '${file.name}')">
breadcrumbHTML += `<span ... onclick="fileManager.enterDirectory('/')">根目录</span>`;
```

- **上传流程与存储复用**  
  - `uploadManager.js` 根据文件大小选择直传或断点续传：  
    - 小文件先用 `hashCalculator.calculateFullFileHash` 算 SHA-256，调用 `api.checkQuickUpload`；若后端返回 `canQuickUpload`，直走 `api.quickUpload`，只写元数据，实现 README 所述“存储复用、网络 0 开销”。  
    - 其他情况则执行常规 `api.uploadFile`。  
    - 大文件通过 `initResumableUpload` 拿 `sessionId` 并循环 `uploadChunk`，最后 `completeResumableUpload`。整个过程配有进度条、状态提示与失败告警。  

```41:153:frontend/js/uploadManager.js
const hash = await hashCalculator.calculateFullFileHash(file);
const checkResponse = await api.checkQuickUpload(hash);
if (checkResponse.success && checkResponse.data?.canQuickUpload) {
    const quickResponse = await api.quickUpload(hash, file.name, path);
}
const initResponse = await api.initResumableUpload(file.name, path, file.size);
await api.uploadChunk(sessionId, i, chunk); this.updateUploadProgress(uploadId, progress);
```

- **哈希计算支撑**  
  - `hashCalculator.js` 使用 Web Crypto API（`crypto.subtle.digest('SHA-256')`）对整文件或分块求哈希，为秒传、块级去重、差分同步提供浏览器侧基础能力。  

```3:76:frontend/js/hashCalculator.js
const hashBuffer = await crypto.subtle.digest(this.algorithm, arrayBuffer);
const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
async calculateChunkHashes(file, chunkSize = CONFIG.UPLOAD.BLOCK_SIZE) { ... }
```

- **实时同步（SSE）**  
  - `syncManager.js` 在用户登录后启动，通过带 Bearer Token 的 `fetch` 订阅 `/files/sync`，解析 `text/event-stream` 数据流，按事件类型（`upload/quick-upload/delete/mkdir/...`）弹出提示并触发 `fileManager.loadFiles()`。内置重连策略、最大重试次数与 `stop()` 清理。  

```3:166:frontend/js/syncManager.js
const response = await fetch(`${CONFIG.API_BASE_URL}/files/sync`, { headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' } });
if (line.startsWith('data: ')) { const data = JSON.parse(line.substring(6)); this.handleSyncEvent(data); }
case 'quick-upload': showAlert(`文件已同步: ${name}`, 'success'); fileManager.loadFiles();
```

- **使用指引与结构**  
  - `frontend/README.md` 已提供启动步骤（本地静态服务 `python -m http.server 3000`）、功能覆盖、测试场景（秒传、跨端同步）以及目录结构说明，方便快速定位 `js/css` 模块。

### 四、前后端总体架构

- 前端：静态页面 + 模块化 JS
  - `api.js` 统一封装 `fetch`，自动附加认证头，处理 JSON/Blob 响应与错误
  - `auth.js` 管理登录/注册/退出与页面切换，令牌与用户信息保存在 `localStorage`
  - `fileManager.js` 管理文件列表、目录结构与视图模式切换
  - `uploadManager.js` 选择上传方式：普通上传 + 秒传检查；或断点续传（分片）
  - `syncManager.js` 订阅服务器 SSE 更新，触发前端刷新
- 后端：REST API + 业务服务 + 存储抽象
  - 控制器：`controller/*` 提供认证、文件、协作、健康等接口
  - 服务层：`service/*` 实现业务逻辑（上传/断点续传/差分/加密/用户/文件）
  - 存储层：`storage/StorageService` 接口与 `S3StorageService` 实现，负责对象存储与元数据
  - 安全：`security/*` 提供 JWT 解析与校验、过滤链与异常处理；`SecurityConfig` 配置 CORS 与放行路径

### 五、本地复现实验步骤

#### 1.后端（WSL）
- 进入后端目录：
```bash
cd /mnt/d/EasyCloudDisk-2/server
```
- 设置必要环境变量
```bash
export AWS_ACCESS_KEY_ID="..."
export AWS_SECRET_ACCESS_KEY="..."
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"
```
- 启动并测试编译
```bash
mvn spring-boot:run 
```
- 健康检查
```bash
curl http://localhost:8080/health
```

#### 2.前端（Windows）
- 启动静态服务器：
```bat
cd /d d:\EasyCloudDisk-2\frontend
start.bat
```
- 或者：
```bat
python -m http.server 3000
```
- 打开浏览器访问：`http://localhost:3000`

### 六、实验流程

- 注册并登录 → 进入主页面

- 小文件上传：触发秒传检查，不可秒传则直接表单上传

- 大文件上传：触发断点续传（观察进度与合并）

- 下载一个文件并验证内容；执行删除并确认列表更新

- 如使用 S3，观察后端日志与对象存储中对象的变化（建桶/上传/删除）

  **具体可以参考视频**

  ![](新建文件夹\屏幕截图 2025-11-21 110531.png)

  ![屏幕截图 2025-11-21 110604](新建文件夹\屏幕截图 2025-11-21 110604.png)

  ![屏幕截图 2025-11-21 110635](新建文件夹\屏幕截图 2025-11-21 110635.png)

  ![屏幕截图 2025-11-21 110717](新建文件夹\屏幕截图 2025-11-21 110717.png)

  ![屏幕截图 2025-11-21 110755](新建文件夹\屏幕截图 2025-11-21 110755.png)

  ![屏幕截图 2025-11-21 110822](新建文件夹\屏幕截图 2025-11-21 110822.png)

  ![屏幕截图 2025-11-21 110904](新建文件夹\屏幕截图 2025-11-21 110904.png)

  ![屏幕截图 2025-11-21 110948](新建文件夹\屏幕截图 2025-11-21 110948.png)

  ![屏幕截图 2025-11-21 111044](新建文件夹\屏幕截图 2025-11-21 111044.png)

  ![屏幕截图 2025-11-21 111106](新建文件夹\屏幕截图 2025-11-21 111106.png)

  ![屏幕截图 2025-11-21 111131](新建文件夹\屏幕截图 2025-11-21 111131.png)

  ![屏幕截图 2025-11-21 111222](新建文件夹\屏幕截图 2025-11-21 111222.png)

  ![屏幕截图 2025-11-21 111301](新建文件夹\屏幕截图 2025-11-21 111301.png)

  ![屏幕截图 2025-11-21 111355](新建文件夹\屏幕截图 2025-11-21 111355.png)

  ![屏幕截图 2025-11-21 111416](新建文件夹\屏幕截图 2025-11-21 111416.png)

  ![屏幕截图 2025-11-21 111446](新建文件夹\屏幕截图 2025-11-21 111446.png)

  ![屏幕截图 2025-11-21 111515](新建文件夹\屏幕截图 2025-11-21 111515.png)

  ![屏幕截图 2025-11-21 111756](新建文件夹\屏幕截图 2025-11-21 111756.png)

  ![屏幕截图 2025-11-21 111910](新建文件夹\屏幕截图 2025-11-21 111910.png)

  ![屏幕截图 2025-11-21 112008](新建文件夹\屏幕截图 2025-11-21 112008.png)

  ![屏幕截图 2025-11-21 112026](新建文件夹\屏幕截图 2025-11-21 112026.png)

  ![屏幕截图 2025-11-21 112048](新建文件夹\屏幕截图 2025-11-21 112048.png)

  ![屏幕截图 2025-11-21 112108](新建文件夹\屏幕截图 2025-11-21 112108.png)

  ![屏幕截图 2025-11-21 112141](新建文件夹\屏幕截图 2025-11-21 112141.png)

