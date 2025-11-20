# 故障排除指南

## 常见启动错误及解决方案

### 错误1: 数据库连接失败

**错误信息**:
```
org.postgresql.util.PSQLException: Connection refused
或
java.sql.SQLException: No suitable driver found
```

**原因**: 本地没有安装PostgreSQL或数据库未启动

**解决方案**:

#### 方案A: 使用H2内存数据库（推荐，最简单）

已修改 `application.yml` 使用H2数据库，无需安装PostgreSQL。

如果还是报错，检查 `application.yml` 中的数据库配置是否为：
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
      dialect: org.hibernate.dialect.H2Dialect
```

#### 方案B: 安装并启动PostgreSQL

```bash
# Ubuntu/WSL
sudo apt update
sudo apt install postgresql postgresql-contrib
sudo service postgresql start

# 创建数据库
sudo -u postgres psql
CREATE DATABASE clouddisk;
CREATE USER clouddisk WITH PASSWORD '123456';
GRANT ALL PRIVILEGES ON DATABASE clouddisk TO clouddisk;
\q
```

### 错误2: AWS凭证错误

**错误信息**:
```
Unable to load credentials from any provider
或
InvalidAccessKeyId
```

**解决方案**:
```bash
# 确保设置了环境变量
export AWS_ACCESS_KEY_ID="AKIARCSPQ2MSDC2UES4A"
export AWS_SECRET_ACCESS_KEY="你的secret-key"
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"

# 验证环境变量
echo $AWS_ACCESS_KEY_ID
echo $AWS_SECRET_ACCESS_KEY
```

### 错误3: 端口被占用

**错误信息**:
```
Port 8080 is already in use
```

**解决方案**:
```bash
# 查找占用端口的进程
# Linux/WSL
lsof -i :8080
# 或
netstat -tlnp | grep 8080

# 杀死进程
kill -9 <PID>

# 或修改端口（在application.yml中）
server:
  port: 8081
```

### 错误4: Java版本不匹配

**错误信息**:
```
UnsupportedClassVersionError
```

**解决方案**:
```bash
# 检查Java版本（需要Java 21+）
java -version

# 如果版本不对，安装Java 21
# Ubuntu/WSL
sudo apt update
sudo apt install openjdk-21-jdk

# 设置JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

### 错误5: Maven依赖下载失败

**错误信息**:
```
Could not resolve dependencies
```

**解决方案**:
```bash
# 清理并重新下载依赖
mvn clean
mvn dependency:resolve

# 如果网络问题，可以配置国内镜像
# 编辑 ~/.m2/settings.xml
```

### 错误6: 查看完整错误信息

如果错误信息被截断，使用以下命令查看完整日志：

```bash
# 方法1: 使用-e参数显示完整堆栈
mvn spring-boot:run -e

# 方法2: 使用-X参数显示详细调试信息
mvn spring-boot:run -X

# 方法3: 查看日志文件
tail -f logs/server.log

# 方法4: 直接运行JAR查看完整错误
mvn clean package -DskipTests
java -jar target/clouddisk-server-1.0.0.jar
```

## 快速诊断步骤

1. **检查Java版本**
   ```bash
   java -version  # 应该是21+
   ```

2. **检查Maven**
   ```bash
   mvn -version
   ```

3. **检查环境变量**
   ```bash
   echo $AWS_ACCESS_KEY_ID
   echo $AWS_SECRET_ACCESS_KEY
   echo $AWS_REGION
   ```

4. **检查端口**
   ```bash
   lsof -i :8080  # 或 netstat -tlnp | grep 8080
   ```

5. **检查数据库配置**
   ```bash
   # 查看application.yml中的数据库配置
   cat src/main/resources/application.yml | grep -A 10 datasource
   ```

6. **清理并重新构建**
   ```bash
   mvn clean
   mvn spring-boot:run
   ```

## 获取帮助

如果以上方法都无法解决，请提供：
1. 完整的错误堆栈（使用 `mvn spring-boot:run -e`）
2. Java版本：`java -version`
3. Maven版本：`mvn -version`
4. 操作系统信息
5. `application.yml` 中的数据库配置

