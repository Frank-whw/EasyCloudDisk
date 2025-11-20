# 本地开发环境配置指南

## 为什么在本地开发是安全的？

✅ **完全独立**：本地运行的服务只在你本地机器上，不会影响EC2上的生产服务
✅ **端口隔离**：本地运行在 `localhost:8080`，EC2运行在 `54.95.61.230:8080`，互不干扰
✅ **数据隔离**：本地使用本地数据库，EC2使用EC2的数据库，数据完全分离

## 本地开发环境设置

### 1. 在WSL中设置环境变量

```bash
# 在WSL中（hanyue@LAPTOP-U67Q73JQ）
cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/server

# 设置AWS凭证（从EC2上获取，或者使用你自己的）
export AWS_ACCESS_KEY_ID="AKIARCSPQ2MSDC2UES4A"
export AWS_SECRET_ACCESS_KEY="你的secret-key"  
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"

# 验证环境变量
echo $AWS_ACCESS_KEY_ID
echo $AWS_REGION
```

### 2. 配置数据库（本地开发）

#### 选项A：使用H2内存数据库（最简单，推荐用于开发）

修改 `server/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
```

**优点**：
- ✅ 无需安装PostgreSQL
- ✅ 启动快速
- ✅ 适合开发测试

**缺点**：
- ⚠️ 重启后数据会丢失（开发环境可以接受）

#### 选项B：使用PostgreSQL（如果需要持久化数据）

```bash
# 在WSL中安装PostgreSQL
sudo apt update
sudo apt install postgresql postgresql-contrib

# 启动PostgreSQL
sudo service postgresql start

# 创建数据库
sudo -u postgres psql
CREATE DATABASE clouddisk;
CREATE USER clouddisk WITH PASSWORD '123456';
GRANT ALL PRIVILEGES ON DATABASE clouddisk TO clouddisk;
\q
```

### 3. 启动本地服务

```bash
# 在WSL中
cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/server

# 确保环境变量已设置
export AWS_ACCESS_KEY_ID="AKIARCSPQ2MSDC2UES4A"
export AWS_SECRET_ACCESS_KEY="你的secret-key"
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"

# 启动服务
mvn spring-boot:run

# 或者使用启动脚本
chmod +x start.sh
./start.sh
```

### 4. 验证本地服务

```bash
# 在WSL中测试
curl http://localhost:8080/health

# 应该返回：
# {"database":true,"storage":true,"status":"UP"}
```

### 5. 配置前端连接本地后端

修改 `frontend/js/config.js`：

```javascript
const CONFIG = {
    // 本地开发环境
    API_BASE_URL: 'http://localhost:8080',
    
    // 生产环境（EC2）
    // API_BASE_URL: 'http://54.95.61.230:8080',
    ...
};
```

### 6. 启动前端

```bash
# 在WSL中
cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/frontend

# 启动HTTP服务器
python3 -m http.server 3000

# 在浏览器访问
# http://localhost:3000
```

## 开发工作流

### 日常开发流程

1. **启动本地后端**
   ```bash
   cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/server
   export AWS_ACCESS_KEY_ID="..."
   export AWS_SECRET_ACCESS_KEY="..."
   export AWS_REGION="ap-northeast-1"
   export AWS_S3_BUCKET="clouddisk-test-1762861672"
   mvn spring-boot:run
   ```

2. **启动本地前端**
   ```bash
   cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/frontend
   python3 -m http.server 3000
   ```

3. **访问应用**
   - 前端：http://localhost:3000
   - 后端API：http://localhost:8080
   - 健康检查：http://localhost:8080/health

4. **调试和测试**
   - 修改代码
   - 重启服务（Ctrl+C 然后重新运行）
   - 测试功能

### 部署到EC2（当开发完成后）

```bash
# 1. 打包
cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/server
mvn clean package -DskipTests

# 2. 上传到EC2
scp target/clouddisk-server-1.0.0.jar myec2:~/clouddisk/

# 3. 在EC2上重启服务
ssh myec2
cd ~/clouddisk
./server-startup.sh restart
```

## 环境变量持久化（可选）

为了避免每次都要设置环境变量，可以添加到 `~/.bashrc`：

```bash
# 编辑 ~/.bashrc
nano ~/.bashrc

# 添加以下内容
export AWS_ACCESS_KEY_ID="AKIARCSPQ2MSDC2UES4A"
export AWS_SECRET_ACCESS_KEY="你的secret-key"
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"

# 保存后重新加载
source ~/.bashrc
```

## 常见问题

### Q: 如何获取AWS Secret Access Key？

**A**: 如果你没有保存，可以：
1. 在EC2上查看：`cat ~/.aws/credentials`
2. 或者在AWS控制台重新生成（注意：旧key会失效）

### Q: 本地和EC2使用同一个S3 Bucket会冲突吗？

**A**: 不会冲突，因为：
- 每个用户的数据通过 `userId` 隔离
- 文件通过路径前缀区分（如 `user-{userId}/...`）
- 但建议开发时使用测试bucket，避免影响生产数据

### Q: 如何同时运行本地和EC2服务？

**A**: 完全可以：
- 本地：`localhost:8080`（开发用）
- EC2：`54.95.61.230:8080`（生产用）
- 前端通过修改 `config.js` 切换连接

### Q: 本地开发会影响EC2吗？

**A**: **绝对不会**！它们是完全独立的：
- 不同的服务器
- 不同的端口
- 不同的数据库（如果配置不同）
- 互不干扰

## 快速启动脚本

创建一个 `dev-start.sh` 脚本：

```bash
#!/bin/bash
cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/server

export AWS_ACCESS_KEY_ID="AKIARCSPQ2MSDC2UES4A"
export AWS_SECRET_ACCESS_KEY="你的secret-key"
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"

echo "Starting local development server..."
mvn spring-boot:run
```

使用方法：
```bash
chmod +x dev-start.sh
./dev-start.sh
```

