# EasyCloudDisk API 测试套件

本目录包含所有后端 API 功能的测试脚本。

## 测试脚本说明

### 1. test_health.sh
- **功能**: 测试健康检查接口
- **接口**: `GET /health`
- **说明**: 验证服务器和 S3 连接状态

### 2. test_auth_register.sh
- **功能**: 测试用户注册接口
- **接口**: `POST /auth/register`
- **说明**: 创建新的测试用户（每次生成唯一的邮箱和密码）

### 3. test_auth_login.sh
- **功能**: 测试用户登录接口
- **接口**: `POST /auth/login`
- **说明**: 使用已注册的用户进行登录，获取认证令牌

### 4. test_file_upload.sh
- **功能**: 测试文件上传接口
- **接口**: `POST /files/upload`
- **说明**: 上传测试文件（每次生成唯一的文件名和内容）

### 5. test_file_list.sh
- **功能**: 测试文件列表接口
- **接口**: `GET /files`
- **说明**: 获取当前用户的所有文件列表

### 6. test_file_download.sh
- **功能**: 测试文件下载接口
- **接口**: `GET /files/{fileId}/download`
- **说明**: 下载已上传的测试文件

### 7. test_file_delete.sh
- **功能**: 测试文件删除接口
- **接口**: `DELETE /files/{fileId}`
- **说明**: 删除已上传的测试文件

### 8. test_quick_upload.sh
- **功能**: 测试文件级去重（秒传）接口
- **接口**: `POST /files/quick-check`, `POST /files/quick-upload`
- **说明**: 测试文件级去重功能，验证相同哈希的文件可以秒传

### 9. test_chunk_deduplication.sh
- **功能**: 测试块级去重功能
- **接口**: `POST /files/upload`
- **说明**: 上传两个相同内容的文件，验证块级去重（共享存储块）

### 10. test_resumable_upload.sh
- **功能**: 测试断点续传功能
- **接口**: `POST /files/resumable/init`, `POST /files/resumable/{sessionId}/chunk/{chunkIndex}`, `POST /files/resumable/{sessionId}/complete`
- **说明**: 测试大文件的断点续传，分块上传后合并

### 11. test_resumable_sessions.sh
- **功能**: 测试断点续传会话列表接口
- **接口**: `GET /files/resumable/sessions`
- **说明**: 获取用户的所有上传会话及进度信息

### 12. test_delta_sync.sh
- **功能**: 测试差分同步（增量同步）功能
- **接口**: `GET /files/{fileId}/signatures`, `POST /files/{fileId}/delta`
- **说明**: 获取文件块签名，应用差分更新

### 13. test_encryption.sh
- **功能**: 测试数据加密功能
- **接口**: `POST /files/upload-encrypted`, `GET /files/{fileId}/encryption`, `POST /files/convergent-check`
- **说明**: 上传加密文件，获取加密元数据，检查收敛加密

### 14. run_all_tests.sh
- **功能**: 运行所有测试的主脚本
- **说明**: 按顺序执行所有测试，生成测试报告

## 使用方法

### 配置 API 地址（可选）
```bash
export API_BASE_URL="http://localhost:8080"
```

### 运行单个测试
```bash
# 运行健康检查测试
./test/test_health.sh

# 运行用户注册测试
./test/test_auth_register.sh

# 运行文件上传测试
./test/test_file_upload.sh
```

### 运行所有测试（推荐）
```bash
# 确保脚本有执行权限
chmod +x test/*.sh

# 运行所有测试
./test/run_all_tests.sh
```

## 测试特性

### 唯一性保证
- 每次测试生成唯一的测试数据
  - 邮箱: `test_{timestamp}_{random}@example.com`
  - 密码: `Test123456_{timestamp}`
  - 文件名: `test_file_{timestamp}_{random}.txt`
- 使用时间戳和随机数确保测试数据不重复

### 测试正规性
- 完整的错误处理
- HTTP 状态码验证
- 响应格式验证
- 清晰的输出和日志
- 正确的退出码

### 测试依赖
测试脚本之间存在依赖关系，建议按以下顺序执行：
1. `test_health.sh` - 基础检查
2. `test_auth_register.sh` - 创建用户
3. `test_auth_login.sh` - 获取令牌
4. `test_file_upload.sh` - 上传文件
5. `test_file_list.sh` - 列出文件
6. `test_file_download.sh` - 下载文件
7. `test_file_delete.sh` - 删除文件
8. `test_quick_upload.sh` - 文件级去重（需要先上传文件）
9. `test_chunk_deduplication.sh` - 块级去重（独立测试）
10. `test_resumable_upload.sh` - 断点续传（独立测试）
11. `test_resumable_sessions.sh` - 会话列表（可与断点续传一起测试）
12. `test_delta_sync.sh` - 差分同步（需要先上传分块文件）
13. `test_encryption.sh` - 数据加密（独立测试）

使用 `run_all_tests.sh` 会自动处理依赖关系，按顺序执行所有测试。

## 测试报告

运行 `run_all_tests.sh` 会生成测试报告：
- 文件名: `test_report_YYYYMMDD_HHMMSS.txt`
- 包含所有测试的详细输出
- 包含测试摘要（通过/失败统计）

## 环境变量

测试脚本支持以下环境变量：

- `API_BASE_URL`: API 服务器地址（默认: `http://localhost:8080`）
- `AUTH_TOKEN`: 认证令牌（由登录测试自动设置）
- `TEST_EMAIL`: 测试用户邮箱（由注册测试自动设置）
- `TEST_PASSWORD`: 测试用户密码（由注册测试自动设置）

## 注意事项

1. **服务器必须运行**: 确保后端服务器在 `API_BASE_URL` 指定的地址运行
2. **数据库连接**: 确保数据库（PostgreSQL）已启动
3. **AWS 配置**: 确保 AWS S3 凭证已正确配置
4. **清理测试数据**: 测试完成后，测试用户和文件会保留在数据库中，可手动清理

## 故障排除

### 测试失败：服务器连接失败
- 检查服务器是否启动: `curl http://localhost:8080/health`
- 检查 `API_BASE_URL` 环境变量

### 测试失败：认证失败
- 确保先运行 `test_auth_register.sh` 或 `test_auth_login.sh`
- 检查 `AUTH_TOKEN` 环境变量是否设置

### 测试失败：文件操作失败
- 检查 AWS S3 配置是否正确
- 检查 Bucket 是否存在且可访问
- 检查区域配置是否正确

## 贡献

添加新测试时，请遵循以下规范：
1. 文件名以 `test_` 开头
2. 使用唯一的测试数据（时间戳+随机数）
3. 包含完整的错误处理
4. 输出清晰的测试结果
5. 返回正确的退出码（0=成功，非0=失败）
6. 如果设置了环境变量（如 TOKEN、FILE_ID），使用 `ENV_FILE` 机制传递给其他测试
7. 如果测试依赖其他测试的结果，自动运行依赖的测试或优雅处理缺失

