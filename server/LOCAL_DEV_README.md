# 本地开发环境配置（WSL）

## ✅ 完全安全，不会影响EC2

在本地WSL运行后端服务**完全安全**，因为：
- ✅ 本地服务运行在 `localhost:8080`，EC2运行在 `54.95.61.230:8080`，互不干扰
- ✅ 本地使用本地数据库，EC2使用EC2的数据库，数据完全分离
- ✅ 即使使用同一个S3 bucket，数据通过userId隔离，不会冲突
- ✅ 本地调试不会影响EC2上的生产服务

## 快速开始

### 1. 获取AWS Secret Key

```bash
# 方法1: 从EC2获取（推荐）
ssh myec2 'cat ~/.aws/credentials'

# 方法2: 从AWS控制台获取
# 登录AWS控制台 -> IAM -> 用户 -> 安全凭证
```

### 2. 设置环境变量

```bash
# 在WSL中
cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/server

# 设置环境变量
export AWS_ACCESS_KEY_ID="AKIARCSPQ2MSDC2UES4A"
export AWS_SECRET_ACCESS_KEY="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"
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"
```

### 3. 配置数据库（使用H2内存数据库，最简单）

编辑 `server/src/main/resources/application.yml`，找到数据库配置部分：

```yaml
spring:
  datasource:
    # 使用H2内存数据库（开发用）
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  jpa:
    hibernate:
      ddl-auto: create-drop  # 启动时创建表，关闭时删除
    show-sql: true
```

**注意**：H2数据库重启后数据会丢失，适合开发调试。

### 4. 启动本地服务

```bash
# 使用启动脚本（推荐）
chmod +x dev-start.sh
./dev-start.sh

# 或直接使用Maven
mvn spring-boot:run
```

### 5. 验证服务

```bash
# 在另一个终端测试
curl http://localhost:8080/health

# 应该返回：
# {"database":true,"storage":true,"status":"UP"}
```

### 6. 配置前端连接本地后端

前端已经配置好了！`frontend/js/config.js` 中已经设置为：
```javascript
API_BASE_URL: 'http://localhost:8080',  // 本地开发
```

### 7. 启动前端

```bash
# 在另一个终端
cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/frontend
python3 -m http.server 3000
```

### 8. 访问应用

- 前端：http://localhost:3000
- 后端API：http://localhost:8080
- 健康检查：http://localhost:8080/health

## 开发工作流

### 日常开发

1. **启动后端**（终端1）
   ```bash
   cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/server
   export AWS_ACCESS_KEY_ID="AKIARCSPQ2MSDC2UES4A"
   export AWS_SECRET_ACCESS_KEY="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"
   export AWS_REGION="ap-northeast-1"
   export AWS_S3_BUCKET="clouddisk-test-1762861672"
   ./dev-start.sh
   ```

2. **启动前端**（终端2）
   ```bash
   cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/frontend
   python3 -m http.server 3000
   ```

3. **开发调试**
   - 修改代码
   - 重启后端（Ctrl+C 然后重新运行）
   - 刷新前端页面测试

### 环境变量持久化（可选）

为了避免每次都要设置环境变量，可以添加到 `~/.bashrc`：

```bash
# 编辑 ~/.bashrc
nano ~/.bashrc

# 添加以下内容（在文件末尾）
# EasyCloudDisk 开发环境变量
export AWS_ACCESS_KEY_ID="AKIARCSPQ2MSDC2UES4A"
export AWS_SECRET_ACCESS_KEY="你的secret-key"
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"

# 保存后重新加载
source ~/.bashrc
```

## 常见问题

### Q: 如何获取AWS Secret Access Key？

**A**: 
```bash
# 在EC2上查看
ssh myec2 'cat ~/.aws/credentials'

# 或者在AWS控制台：
# IAM -> 用户 -> 安全凭证 -> 创建访问密钥
```

### Q: 本地和EC2使用同一个S3 Bucket会冲突吗？

**A**: 不会冲突：
- 每个用户的数据通过 `userId` 隔离
- 文件路径包含用户ID前缀（如 `user-{userId}/...`）
- 但建议开发时使用测试数据，避免影响生产

### Q: 如何切换回EC2后端？

**A**: 修改 `frontend/js/config.js`：
```javascript
// 本地开发
// API_BASE_URL: 'http://localhost:8080',

// 生产环境（EC2）
API_BASE_URL: 'http://54.95.61.230:8080',
```

### Q: 本地开发会影响EC2吗？

**A**: **绝对不会**！它们是完全独立的服务，互不干扰。

### Q: 数据库数据会丢失吗？

**A**: 如果使用H2内存数据库，重启后会丢失。这是正常的，适合开发调试。如果需要持久化，可以配置PostgreSQL。

## 下一步

开发完成后，可以部署到EC2：
1. 打包：`mvn clean package -DskipTests`
2. 上传：`scp target/clouddisk-server-1.0.0.jar myec2:~/clouddisk/`
3. 在EC2上重启服务

