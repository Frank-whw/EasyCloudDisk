# 后端启动成功！下一步操作

## ✅ 后端已成功启动

从日志可以看到：
- ✅ 数据库表已创建
- ✅ Tomcat服务器在 `http://localhost:8080` 运行
- ✅ 应用启动成功

## 快速验证

### 1. 测试健康检查

在另一个终端运行：

```bash
curl http://localhost:8080/health
```

应该返回：
```json
{
  "database": true,
  "storage": true,
  "status": "UP"
}
```

### 2. 测试API（可选）

```bash
# 注册用户
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123456"}'

# 登录
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123456"}'
```

或使用测试脚本：
```bash
chmod +x test-api.sh
./test-api.sh
```

## 启动前端

现在可以启动前端应用了：

### 1. 打开新终端（保持后端运行）

```bash
# 在WSL中
cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/frontend

# 启动前端服务器
python3 -m http.server 3000
```

### 2. 访问应用

在浏览器中打开：**http://localhost:3000**

### 3. 开始使用

1. 注册账号
2. 登录
3. 上传文件
4. 测试功能

## 开发工作流

### 日常开发

1. **终端1 - 运行后端**（已启动）
   ```bash
   cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/server
   ./dev-start.sh
   ```

2. **终端2 - 运行前端**
   ```bash
   cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/frontend
   python3 -m http.server 3000
   ```

3. **修改代码**
   - 修改后端代码后，按 `Ctrl+C` 停止，然后重新运行 `./dev-start.sh`
   - 修改前端代码后，刷新浏览器即可

### 停止服务

- **停止后端**：在运行后端的终端按 `Ctrl+C`
- **停止前端**：在运行前端的终端按 `Ctrl+C`

## 重要提示

### 数据库数据

⚠️ **注意**：当前使用H2内存数据库，重启后端后数据会丢失。这是正常的，适合开发调试。

如果需要持久化数据：
1. 安装PostgreSQL
2. 修改 `application.yml` 使用PostgreSQL配置

### 环境变量

每次重新打开终端都需要重新设置环境变量，或者添加到 `~/.bashrc`：

```bash
# 编辑 ~/.bashrc
nano ~/.bashrc

# 添加环境变量
export AWS_ACCESS_KEY_ID="AKIARCSPQ2MSDC2UES4A"
export AWS_SECRET_ACCESS_KEY="你的secret-key"
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"

# 保存后重新加载
source ~/.bashrc
```

## 常见问题

### Q: 如何查看后端日志？

**A**: 日志会直接输出到终端。如果需要查看文件日志：
```bash
tail -f logs/server.log
```

### Q: 如何修改端口？

**A**: 编辑 `server/src/main/resources/application.yml`：
```yaml
server:
  port: 8081  # 修改为你想要的端口
```

### Q: 如何查看API文档？

**A**: 如果配置了Swagger，访问：
- http://localhost:8080/swagger-ui.html

### Q: 如何调试？

**A**: 
- 后端日志级别已在 `application.yml` 中设置为 DEBUG
- 可以在IDE中直接运行 `ServerApplication.main()` 进行调试

## 下一步

1. ✅ 后端已启动
2. ⏭️ 启动前端（新终端运行 `python3 -m http.server 3000`）
3. ⏭️ 访问 http://localhost:3000
4. ⏭️ 开始测试功能

祝开发顺利！🎉

