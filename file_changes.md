# 新功能实现代码修改总结

本文档总结了为实现**文件版本控制**和**共享协作**功能对原有代码所做的修改。

## 一、核心功能概述

### 1.1 文件版本控制
- 历史版本保留：每次文件更新自动创建版本快照
- 版本查看与下载：支持查看和下载任意历史版本
- 版本恢复：可将任意历史版本恢复为当前版本（生成新版本）

### 1.2 冲突解决策略
- 乐观锁机制：所有覆盖式上传必须携带 `baseVersion`
- 版本冲突检测：服务器版本与客户端版本不一致时抛出 `VERSION_CONFLICT`
- 冲突信息反馈：返回服务器版本、客户端版本和更新时间，便于客户端处理

### 1.3 文件/目录共享
- 权限管理：支持 READ/WRITE 两种权限级别
- 目录递归共享：目录共享默认包含子目录和文件
- 共享过期：支持设置共享过期时间
- 共享列表：查看"与我共享"的资源列表

---

## 二、数据模型层修改

### 2.1 新增实体类

#### `FileVersion` - 文件版本实体
**路径**: `server/src/main/java/com/clouddisk/entity/FileVersion.java`

**作用**: 记录文件每个历史版本的元数据信息

**关键字段**:
- `versionId`: 版本记录ID
- `fileId`: 关联的文件ID
- `versionNumber`: 版本号
- `storageKey`: 存储键（固定为"chunked"）
- `fileSize`: 文件大小
- `contentHash`: 内容哈希值
- `createdAt`: 创建时间

#### `SharedResource` - 共享资源实体
**路径**: `server/src/main/java/com/clouddisk/entity/SharedResource.java`

**作用**: 记录文件/目录的共享关系

**关键字段**:
- `shareId`: 共享记录ID
- `fileId`: 被共享的文件/目录ID
- `ownerId`: 所有者用户ID
- `targetUserId`: 目标用户ID
- `permission`: 权限类型（READ/WRITE）
- `resourceType`: 资源类型（FILE/DIRECTORY）
- `resourcePath`: 资源完整路径
- `includeSubtree`: 是否包含子树（目录共享）
- `expiresAt`: 过期时间（可选）

### 2.2 修改现有实体

#### `FileEntity` - 文件实体
**修改点**: 新增 `version` 字段

```java
@Column(name = "version", nullable = false)
private int version;
```

**作用**: 记录文件的当前版本号，用于乐观锁冲突检测

### 2.3 新增枚举类

#### `SharePermission` - 共享权限枚举
- `READ`: 只读权限
- `WRITE`: 读写权限

#### `ShareResourceType` - 共享资源类型枚举
- `FILE`: 文件
- `DIRECTORY`: 目录

---

## 三、数据访问层修改

### 3.1 新增 Repository 接口

#### `FileVersionRepository`
**路径**: `server/src/main/java/com/clouddisk/repository/FileVersionRepository.java`

**关键方法**:
- `findAllByFileIdOrderByVersionNumberDesc(String fileId)`: 按版本号降序查询文件的所有版本

#### `SharedResourceRepository`
**路径**: `server/src/main/java/com/clouddisk/repository/SharedResourceRepository.java`

**关键方法**:
- `findAllByTargetUserId(String userId)`: 查询用户被共享的资源
- `findAllByFileId(String fileId)`: 查询文件的所有共享记录
- `findByShareIdAndOwnerId(String shareId, String ownerId)`: 根据共享ID和所有者查询

---

## 四、服务层修改

### 4.1 FileService - 文件服务

#### 4.1.1 修改的方法

**`upload(MultipartFile, String, String, Integer)`**
- **新增参数**: `baseVersion` - 客户端当前版本号
- **修改逻辑**:
  - 覆盖已存在文件时，必须提供 `baseVersion`
  - 调用 `ensureVersionMatch()` 进行版本校验
  - 版本匹配后，版本号自增
  - 调用 `persistFile()` 保存文件并创建版本记录

**`download(String, String)`**
- **修改逻辑**: 
  - 使用 `collaborationService.requireFileAccess()` 替代直接查询，支持共享权限检查
  - 支持从块存储重组文件（`chunked` 格式）

**`delete(String, String)`**
- **修改逻辑**:
  - 使用 `collaborationService.requireFileAccess()` 检查权限
  - 删除文件时，同时删除所有版本记录和共享记录
  - 支持块存储的引用计数清理

#### 4.1.2 新增的方法

**`updateFile(String, MultipartFile, String, Integer)`**
- **功能**: 通过文件ID直接更新文件内容（支持共享写权限）
- **参数**: 
  - `fileId`: 文件ID
  - `file`: 文件数据
  - `userId`: 用户ID
  - `baseVersion`: 客户端版本号
- **逻辑**: 
  - 权限检查（支持共享写权限）
  - 版本冲突检测
  - 版本号自增并保存新版本

**`listVersions(String, String)`**
- **功能**: 列出文件的所有历史版本
- **返回**: `List<FileVersionDto>`

**`downloadVersion(String, int, String)`**
- **功能**: 下载指定版本的文件内容
- **逻辑**: 从块存储重组指定版本的文件数据

**`restoreVersion(String, int, String)`**
- **功能**: 将指定版本恢复为当前版本
- **逻辑**:
  1. 从块存储读取指定版本的数据
  2. 创建新版本（版本号自增）
  3. 将恢复的数据保存为新版本

**`ensureVersionMatch(FileEntity, Integer)`** (私有方法)
- **功能**: 版本冲突检测
- **逻辑**:
  - 如果 `baseVersion` 为 null，抛出 `VERSION_REQUIRED`
  - 如果版本不匹配，抛出 `VERSION_CONFLICT`，并返回服务器版本、客户端版本和更新时间

**`persistFile(FileEntity, byte[], String)`** (私有方法)
- **功能**: 统一文件持久化逻辑
- **逻辑**:
  1. 设置存储键为 `"chunked"`
  2. 调用 `chunkService.storeFileInChunks()` 存储文件块
  3. 创建 `FileVersion` 记录并保存

### 4.2 CollaborationService - 协作服务（新增）

**路径**: `server/src/main/java/com/clouddisk/service/CollaborationService.java`

#### 核心方法

**`requireFileAccess(String, String, SharePermission)`**
- **功能**: 判断用户是否对文件拥有所需权限
- **逻辑**:
  1. 首先检查是否为文件所有者
  2. 如果不是所有者，检查共享权限
  3. 支持目录共享的递归访问判断
  4. 检查共享是否过期

**`createShare(String, String, ShareRequest)`**
- **功能**: 创建文件/目录共享
- **逻辑**:
  - 验证文件所有权
  - 验证目标用户存在
  - 目录共享默认设置 `includeSubtree = true`
  - 保存共享记录

**`listShares(String, String)`**
- **功能**: 列出文件的所有共享记录

**`revokeShare(String, String)`**
- **功能**: 撤销指定共享

**`removeAllSharesForFile(String)`**
- **功能**: 删除文件的所有共享记录（文件删除时调用）

**`listSharedWithMe(String)`**
- **功能**: 获取"与我共享"的资源列表
- **逻辑**:
  - 过滤过期共享
  - 填充共享者邮箱、权限等元数据

**`hasSharePermission(FileEntity, String, SharePermission)`** (私有方法)
- **功能**: 检查用户是否拥有共享权限
- **逻辑**:
  - 支持文件级共享直接匹配
  - 支持目录级共享的路径前缀匹配

### 4.3 ChunkService - 块服务

#### 修改的方法

**`storeFileInChunks(String, Integer, byte[], String, boolean)`**
- **新增参数**: `versionNumber` - 版本号
- **修改逻辑**: 在创建 `FileChunkMapping` 时关联版本号

**`assembleFile(String, Integer)`**
- **新增参数**: `versionNumber` - 版本号
- **修改逻辑**: 根据文件ID和版本号查询块映射，支持版本化重组

---

## 五、控制器层修改

### 5.1 FileController

#### 5.1.1 修改的端点

**`POST /files/upload`**
- **新增参数**: `baseVersion` (可选)
- **说明**: 覆盖已存在文件时必须提供

**`GET /files/{fileId}/download`**
- **修改**: 内部使用 `collaborationService.requireFileAccess()` 检查权限

**`DELETE /files/{fileId}`**
- **修改**: 内部使用 `collaborationService.requireFileAccess()` 检查权限

#### 5.1.2 新增的端点

**`PUT /files/{fileId}`**
- **功能**: 通过文件ID更新文件内容（支持共享协作）
- **参数**: 
  - `fileId`: 文件ID
  - `file`: 文件数据
  - `baseVersion`: 客户端版本号（必需）
- **返回**: 更新后的文件元数据

**`GET /files/{fileId}/versions`**
- **功能**: 获取文件的历史版本列表
- **返回**: `List<FileVersionDto>`

**`GET /files/{fileId}/versions/{versionNumber}/download`**
- **功能**: 下载指定版本的文件内容
- **返回**: 文件流

**`POST /files/{fileId}/versions/{versionNumber}/restore`**
- **功能**: 将指定版本恢复为当前版本
- **返回**: 恢复后的文件元数据

**`GET /files/shared`**
- **功能**: 查看"与我共享"的文件/目录列表
- **返回**: `List<FileMetadataDto>`（包含共享者邮箱、权限等信息）

**`POST /files/{fileId}/share`**
- **功能**: 创建文件/目录共享
- **请求体**: `ShareRequest` (目标邮箱、权限、过期时间)
- **返回**: `ShareResponseDto`

**`GET /files/{fileId}/shares`**
- **功能**: 查看文件的所有共享记录
- **返回**: `List<ShareResponseDto>`

**`DELETE /files/{fileId}/shares/{shareId}`**
- **功能**: 撤销指定共享

---

## 六、DTO 层修改

### 6.1 新增 DTO

#### `FileVersionDto`
**路径**: `server/src/main/java/com/clouddisk/dto/FileVersionDto.java`

**字段**:
- `versionNumber`: 版本号
- `fileSize`: 文件大小
- `contentHash`: 内容哈希
- `createdAt`: 创建时间

#### `ShareRequest`
**路径**: `server/src/main/java/com/clouddisk/dto/ShareRequest.java`

**字段**:
- `targetEmail`: 目标用户邮箱
- `permission`: 权限类型（READ/WRITE）
- `expiresAt`: 过期时间（可选）

#### `ShareResponseDto`
**路径**: `server/src/main/java/com/clouddisk/dto/ShareResponseDto.java`

**字段**:
- `shareId`: 共享ID
- `fileId`: 文件ID
- `fileName`: 文件名
- `resourceType`: 资源类型
- `permission`: 权限
- `ownerEmail`: 所有者邮箱
- `targetEmail`: 目标用户邮箱
- `createdAt`: 创建时间
- `expiresAt`: 过期时间
- `includeSubtree`: 是否包含子树

### 6.2 修改现有 DTO

#### `FileMetadataDto`
**新增字段**:
- `version`: 版本号
- `shared`: 是否为共享资源
- `shareId`: 共享ID（仅共享资源）
- `permission`: 权限（仅共享资源）
- `ownerEmail`: 所有者邮箱（仅共享资源）

---

## 七、异常处理修改

### 7.1 ErrorCode 枚举

**路径**: `server/src/main/java/com/clouddisk/exception/ErrorCode.java`

**新增错误码**:
- `VERSION_REQUIRED("缺少文件版本信息")`: 覆盖文件时未提供 baseVersion
- `VERSION_CONFLICT("文件版本冲突")`: 客户端版本与服务器版本不一致
- `SHARE_NOT_FOUND("共享信息不存在")`: 共享记录不存在
- `ACCESS_DENIED("无权限访问")`: 用户无权访问资源

---

## 八、测试代码

### 8.1 新增测试类

#### `FileServiceTest`
**路径**: `server/src/test/java/com/clouddisk/service/FileServiceTest.java`

**测试用例**:
1. `uploadExistingFileWithoutBaseVersionShouldThrowVersionRequired`
   - 验证覆盖已存在文件时，未提供 baseVersion 应抛出异常

2. `restoreVersionShouldPersistNewChunkedSnapshot`
   - 验证版本恢复功能，确保恢复后创建新版本并正确存储块数据

#### `CollaborationServiceTest`
**路径**: `server/src/test/java/com/clouddisk/service/CollaborationServiceTest.java`

**测试用例**:
1. `requireFileAccessShouldAllowDirectoryShareSubtree`
   - 验证目录共享的递归访问权限判断

2. `listSharedWithMeShouldFilterExpiredSharesAndEnrichMetadata`
   - 验证"与我共享"列表过滤过期共享并填充元数据

### 8.2 构建配置修改

#### `pom.xml`
**修改**: 添加 `maven-surefire-plugin` 插件配置

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.1.2</version>
</plugin>
```

**作用**: 确保 JUnit 5 测试能够正确执行

---

## 九、数据库变更

### 9.1 新增表

#### `file_versions` 表
- 存储文件的历史版本元数据
- 主键: `version_id`
- 索引: `file_id`（用于查询文件的所有版本）

#### `shared_resources` 表
- 存储文件/目录的共享记录
- 主键: `share_id`
- 索引: 
  - `target_user_id`（用于查询用户被共享的资源）
  - `owner_id`（用于查询用户共享的资源）

### 9.2 修改表

#### `files` 表
- 新增字段: `version` (INT, NOT NULL, DEFAULT 1)
- 作用: 记录文件的当前版本号

---

## 十、关键设计决策

### 10.1 版本存储策略
- **选择**: 使用块级存储（chunked）存储所有版本
- **优势**: 
  - 利用块级去重，相同内容只存储一次
  - 版本恢复时只需重组块映射，无需复制数据
  - 节省存储空间

### 10.2 冲突解决策略
- **选择**: 乐观锁机制（基于版本号）
- **实现**: 
  - 客户端上传时携带 `baseVersion`
  - 服务器校验版本匹配后才允许更新
  - 版本不匹配时返回详细冲突信息

### 10.3 共享权限模型
- **选择**: 基于路径的权限检查
- **实现**:
  - 文件级共享：直接匹配文件ID
  - 目录级共享：路径前缀匹配（支持递归）
  - 权限继承：目录共享默认包含子资源

### 10.4 版本恢复策略
- **选择**: 恢复时创建新版本，而非覆盖当前版本
- **优势**: 
  - 保留完整的版本历史
  - 支持多次恢复操作
  - 避免数据丢失

---

## 十一、向后兼容性

### 11.1 API 兼容性
- `POST /files/upload` 的 `baseVersion` 参数为可选，新文件上传不受影响
- 已存在的文件首次覆盖时需要提供 `baseVersion`，否则抛出异常

### 11.2 数据兼容性
- 现有文件的 `version` 字段默认为 1
- 旧格式文件（非 chunked）仍支持下载，但新上传的文件统一使用 chunked 格式

---

## 十二、运行测试

### 12.1 运行所有测试
```bash
cd server
mvn test
```

### 12.2 运行特定测试类
```bash
# 运行 FileServiceTest
mvn -Dtest=FileServiceTest test

# 运行 CollaborationServiceTest
mvn -Dtest=CollaborationServiceTest test
```
![image-20251120082645694](C:\Users\32535\AppData\Roaming\Typora\typora-user-images\image-20251120082645694.png)

![image-20251120082705851](C:\Users\32535\AppData\Roaming\Typora\typora-user-images\image-20251120082705851.png)



## 十三、总结

本次修改主要实现了以下功能：

1. **文件版本控制**: 完整的历史版本管理能力
2. **冲突解决**: 基于乐观锁的并发控制机制
3. **共享协作**: 文件/目录的权限管理和共享功能

所有修改均遵循了以下原则：
- 保持向后兼容（尽可能）
- 充分利用现有的块级存储机制
- 统一的权限检查机制
- 完整的测试覆盖

修改涉及的主要文件数量：
- 新增实体类: 2 个
- 新增服务类: 1 个
- 新增 Repository: 2 个
- 新增 DTO: 3 个
- 新增测试类: 2 个
- 修改现有服务: 1 个
- 修改现有控制器: 1 个
- 新增 API 端点: 7 个

