# EasyCloudDisk 后端启动指南

## 前置要求

### 1. 环境要求
- **Java**: JDK 21 或更高版本
- **Maven**: 3.6+ 
- **PostgreSQL**: 12+ （或使用H2内存数据库进行测试）
- **AWS凭证**: Access Key ID 和 Secret Access Key（用于S3存储）

### 2. 检查环境

```bash
# 检查Java版本
java -version
# 应该显示 java version "21" 或更高

# 检查Maven版本
mvn -version
# 应该显示 Apache Maven 3.6+ 

# 检查PostgreSQL（如果使用）
psql --version
```

## 快速启动（本地开发）

### 方式一：使用Maven直接运行（推荐用于开发）

```bash
# 1. 进入server目录
cd server

# 2. 配置环境变量（Windows）
# 在PowerShell中：
$env:AWS_ACCESS_KEY_ID="your-access-key-id"
$env:AWS_SECRET_ACCESS_KEY="your-secret-access-key"
$env:AWS_REGION="ap-northeast-1"
$env:AWS_S3_BUCKET="clouddisk-test-1762861672"

# 在Linux/Mac中：
export AWS_ACCESS_KEY_ID="your-access-key-id"
export AWS_SECRET_ACCESS_KEY="your-secret-access-key"
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"

# 3. 启动应用
mvn spring-boot:run
```

### 方式二：打包后运行JAR文件

```bash
# 1. 进入server目录
cd server

# 2. 打包项目
mvn clean package -DskipTests

# 3. 运行JAR文件
java -jar target/clouddisk-server-1.0.0.jar \
  --aws.access-key-id=your-access-key-id \
  --aws.secret-access-key=your-secret-access-key \
  --aws.region=ap-northeast-1 \
  --aws.s3.bucket-name=clouddisk-test-1762861672
```

## 详细配置

### 1. 数据库配置

#### 选项A：使用PostgreSQL（生产环境推荐）

1. **安装PostgreSQL**
   ```bash
   # Ubuntu/Debian
   sudo apt-get install postgresql postgresql-contrib
   
   # macOS
   brew install postgresql
   
   # Windows: 下载安装包 https://www.postgresql.org/download/windows/
   ```

2. **创建数据库和用户**
   ```sql
   -- 登录PostgreSQL
   sudo -u postgres psql
   
   -- 创建数据库
   CREATE DATABASE clouddisk;
   
   -- 创建用户
   CREATE USER clouddisk WITH PASSWORD '123456';
   
   -- 授权
   GRANT ALL PRIVILEGES ON DATABASE clouddisk TO clouddisk;
   \q
   ```

3. **修改配置文件**
   
   编辑 `server/src/main/resources/application.yml`:
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/clouddisk
       username: clouddisk
       password: 123456
   ```

#### 选项B：使用H2内存数据库（仅用于测试）

修改 `application.yml`:
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
```

**注意**: H2数据库重启后数据会丢失，仅用于开发测试。

### 2. AWS S3配置

#### 方式一：环境变量（推荐）

```bash
# Windows PowerShell
$env:AWS_ACCESS_KEY_ID="your-access-key-id"
$env:AWS_SECRET_ACCESS_KEY="your-secret-access-key"
$env:AWS_REGION="ap-northeast-1"
$env:AWS_S3_BUCKET="clouddisk-test-1762861672"

# Linux/Mac
export AWS_ACCESS_KEY_ID="your-access-key-id"
export AWS_SECRET_ACCESS_KEY="your-secret-access-key"
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"
```

#### 方式二：修改application.yml

编辑 `server/src/main/resources/application.yml`:
```yaml
aws:
  access-key-id: your-access-key-id
  secret-access-key: your-secret-access-key
  region: ap-northeast-1
  s3:
    bucket-name: clouddisk-test-1762861672
```

#### 方式三：AWS凭证文件

创建 `~/.aws/credentials`:
```ini
[default]
aws_access_key_id = your-access-key-id
aws_secret_access_key = your-secret-access-key
```

创建 `~/.aws/config`:
```ini
[default]
region = ap-northeast-1
```

### 3. CORS配置

如果需要允许前端访问，修改 `application.yml`:
```yaml
app:
  cors:
    allowed-origins: http://localhost:3000,http://54.95.61.230:3000
    allow-credentials: true
```

## 在AWS EC2上启动

### 1. 上传代码到EC2

```bash
# 在本地打包
cd server
mvn clean package -DskipTests

# 上传到EC2
scp -i /path/to/your-key.pem target/clouddisk-server-1.0.0.jar ubuntu@54.95.61.230:/home/ubuntu/clouddisk/
```

### 2. SSH连接到EC2

```bash
ssh -i /path/to/your-key.pem ubuntu@54.95.61.230
```

### 3. 在EC2上配置环境

```bash
# 创建应用目录
mkdir -p /home/ubuntu/clouddisk
cd /home/ubuntu/clouddisk

# 设置环境变量（添加到 ~/.bashrc 或 ~/.profile）
export AWS_ACCESS_KEY_ID="your-access-key-id"
export AWS_SECRET_ACCESS_KEY="your-secret-access-key"
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"
export JWT_SECRET="your-jwt-secret-key"

# 重新加载配置
source ~/.bashrc
```

### 4. 使用启动脚本

```bash
# 上传启动脚本
scp -i /path/to/your-key.pem scripts/server-startup.sh ubuntu@54.95.61.230:/home/ubuntu/clouddisk/

# 在EC2上
cd /home/ubuntu/clouddisk
chmod +x server-startup.sh

# 启动服务
./server-startup.sh start

# 查看状态
./server-startup.sh status

# 查看日志
./server-startup.sh logs

# 停止服务
./server-startup.sh stop
```

### 5. 使用systemd（推荐用于生产环境）

创建服务文件 `/etc/systemd/system/clouddisk.service`:

```ini
[Unit]
Description=EasyCloudDisk Server
After=network.target postgresql.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/clouddisk
Environment="AWS_ACCESS_KEY_ID=your-access-key-id"
Environment="AWS_SECRET_ACCESS_KEY=your-secret-access-key"
Environment="AWS_REGION=ap-northeast-1"
Environment="AWS_S3_BUCKET=clouddisk-test-1762861672"
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /home/ubuntu/clouddisk/clouddisk-server-1.0.0.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务：
```bash
sudo systemctl daemon-reload
sudo systemctl enable clouddisk
sudo systemctl start clouddisk
sudo systemctl status clouddisk
```

## 验证启动

### 1. 检查健康状态

```bash
# 本地
curl http://localhost:8080/health

# EC2
curl http://localhost:8080/health
# 或从外部
curl http://54.95.61.230:8080/health
```

应该返回：
```json
{
  "database": true,
  "storage": true,
  "status": "UP"
}
```

### 2. 检查日志

```bash
# 如果使用启动脚本
tail -f app.log

# 如果使用systemd
sudo journalctl -u clouddisk -f
```

### 3. 测试API

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

## 常见问题

### 问题1: Java版本不匹配

**错误**: `UnsupportedClassVersionError`

**解决**: 
```bash
# 检查Java版本
java -version
# 需要Java 21+

# 如果安装了多个Java版本，设置JAVA_HOME
export JAVA_HOME=/path/to/java21
```

### 问题2: 数据库连接失败

**错误**: `Connection refused` 或 `Authentication failed`

**解决**:
1. 检查PostgreSQL是否运行: `sudo systemctl status postgresql`
2. 检查数据库配置是否正确
3. 检查防火墙设置: `sudo ufw allow 5432`

### 问题3: AWS凭证错误

**错误**: `Unable to load credentials`

**解决**:
1. 检查环境变量是否正确设置
2. 检查AWS凭证文件权限: `chmod 600 ~/.aws/credentials`
3. 验证Access Key是否有效

### 问题4: 端口被占用

**错误**: `Port 8080 is already in use`

**解决**:
```bash
# 查找占用端口的进程
# Linux/Mac
lsof -i :8080
# Windows
netstat -ano | findstr :8080

# 杀死进程或修改application.yml中的端口
```

### 问题5: CORS错误

**错误**: 前端无法访问API

**解决**:
1. 检查 `application.yml` 中的CORS配置
2. 确保前端地址在 `allowed-origins` 列表中
3. 检查SecurityConfig中的CORS设置

## 开发模式 vs 生产模式

### 开发模式
- 使用H2内存数据库（快速启动）
- 详细日志输出
- 热重载支持（使用IDE）

### 生产模式
- 使用PostgreSQL
- 优化日志级别
- 使用systemd管理服务
- 配置HTTPS
- 设置防火墙规则

## 性能优化建议

1. **JVM参数调优**
   ```bash
   java -Xms1g -Xmx2g -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar clouddisk-server-1.0.0.jar
   ```

2. **数据库连接池**
   在 `application.yml` 中配置：
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20
         minimum-idle: 5
   ```

3. **启用缓存**
   应用已启用Spring Cache，确保配置了缓存提供者。

## 监控和日志

- **健康检查**: `http://localhost:8080/health`
- **日志文件**: `logs/server.log`
- **应用日志**: 控制台输出

## 下一步

启动成功后，可以：
1. 访问API文档（如果配置了Swagger）: `http://localhost:8080/swagger-ui.html`
2. 启动前端应用进行测试
3. 查看日志确认所有功能正常

