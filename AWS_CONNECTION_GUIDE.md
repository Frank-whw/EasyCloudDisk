# AWS 连接配置指南

本指南将帮助您配置项目以连接 AWS S3 服务。

## 目录
1. [获取 AWS 凭证](#1-获取-aws-凭证)
2. [配置凭证的方法](#2-配置凭证的方法)
3. [创建 S3 Bucket](#3-创建-s3-bucket)
4. [验证连接](#4-验证连接)
5. [常见问题](#5-常见问题)

---

## 1. 获取 AWS 凭证

### 步骤 1.1: 创建 AWS 账户
1. 访问 [AWS 官网](https://aws.amazon.com/)
2. 注册/登录 AWS 账户
3. 如果是学生，可以使用 [AWS Educate](https://aws.amazon.com/education/awseducate/) 获得免费额度

### 步骤 1.2: 创建 IAM 用户（推荐用于本地开发）
1. 登录 AWS 控制台
2. 打开 **IAM (Identity and Access Management)** 服务
3. 点击左侧菜单的 **用户 (Users)**
4. 点击 **创建用户 (Create user)**
5. 输入用户名，例如：`clouddisk-user`
6. 选择 **编程访问 (Programmatic access)**
7. 点击 **下一步**

### 步骤 1.3: 分配权限
1. 选择 **直接附加现有策略 (Attach existing policies directly)**
2. 搜索并选择以下策略（根据需求选择）：
   - **AmazonS3FullAccess** - 完整 S3 访问权限（开发测试用）
   - 或创建自定义策略，仅授予特定 Bucket 的权限（生产环境推荐）

### 步骤 1.4: 获取访问密钥
1. 完成用户创建后，记录以下信息：
   - **访问密钥 ID (Access Key ID)** - 例如：`AKIAIOSFODNN7EXAMPLE`
   - **秘密访问密钥 (Secret Access Key)** - 例如：`wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY`
   - ⚠️ **重要**：秘密访问密钥只会显示一次，请妥善保存！

---

## 2. 配置凭证的方法

项目支持多种方式配置 AWS 凭证，按优先级从高到低：

### 方法 1: 环境变量（推荐用于生产环境）

#### Windows (PowerShell):
```powershell
$env:AWS_ACCESS_KEY_ID="your-access-key-id"
$env:AWS_SECRET_ACCESS_KEY="your-secret-access-key"
$env:AWS_REGION="ap-northeast-1"
$env:AWS_S3_BUCKET="your-bucket-name"
```

#### Windows (CMD):
```cmd
set AWS_ACCESS_KEY_ID=your-access-key-id
set AWS_SECRET_ACCESS_KEY=your-secret-access-key
set AWS_REGION=ap-northeast-1
set AWS_S3_BUCKET=your-bucket-name
```

#### Linux/macOS:
```bash
export AWS_ACCESS_KEY_ID="your-access-key-id"
export AWS_SECRET_ACCESS_KEY="your-secret-access-key"
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="your-bucket-name"
```

#### 永久设置（Windows - 用户环境变量）:
1. 右键 **此电脑** → **属性**
2. 点击 **高级系统设置**
3. 点击 **环境变量**
4. 在 **用户变量** 中添加：
   - `AWS_ACCESS_KEY_ID`
   - `AWS_SECRET_ACCESS_KEY`
   - `AWS_REGION`
   - `AWS_S3_BUCKET`

### 方法 2: AWS 凭证文件（推荐用于本地开发）

#### Windows:
创建文件：`C:\Users\您的用户名\.aws\credentials`
```ini
[default]
aws_access_key_id = your-access-key-id
aws_secret_access_key = your-secret-access-key
```

创建文件：`C:\Users\您的用户名\.aws\config`
```ini
[default]
region = ap-northeast-1
```

#### Linux/macOS:
创建文件：`~/.aws/credentials`
```ini
[default]
aws_access_key_id = your-access-key-id
aws_secret_access_key = your-secret-access-key
```

创建文件：`~/.aws/config`
```ini
[default]
region = ap-northeast-1
```

### 方法 3: 修改 application.yml（仅用于开发测试）

编辑 `server/src/main/resources/application.yml`：

```yaml
aws:
  access-key-id: your-access-key-id
  secret-access-key: your-secret-access-key
  region: ap-northeast-1
  s3:
    bucket-name: your-bucket-name
```

⚠️ **警告**：不要将包含真实凭证的 `application.yml` 提交到 Git！

---

## 3. 创建 S3 Bucket

### 方法 1: 使用 AWS CLI（推荐）

#### 安装 AWS CLI
- Windows: 下载并安装 [AWS CLI Installer](https://awscli.amazonaws.com/AWSCLIV2.msi)
- Linux/macOS: 
  ```bash
  # macOS
  brew install awscli
  
  # Linux
  curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
  unzip awscliv2.zip
  sudo ./aws/install
  ```

#### 配置 AWS CLI
```bash
aws configure
```
按提示输入：
- AWS Access Key ID
- AWS Secret Access Key
- Default region name: `ap-northeast-1` (或您选择的区域)
- Default output format: `json`

#### 创建 Bucket
```bash
# 创建 Bucket（bucket名称必须全局唯一）
aws s3 mb s3://your-bucket-name --region ap-northeast-1

# 验证 Bucket 是否创建成功
aws s3 ls
```

### 方法 2: 使用 AWS 控制台
1. 登录 [AWS 控制台](https://console.aws.amazon.com/)
2. 打开 **S3** 服务
3. 点击 **创建存储桶 (Create bucket)**
4. 输入存储桶名称（必须全局唯一，例如：`clouddisk-bucket-yourname-2024`）
5. 选择区域（例如：`ap-northeast-1` 东京）
6. 取消勾选 **阻止所有公共访问**（如果需要公开访问，否则保持勾选）
7. 点击 **创建存储桶**

### 方法 3: 让应用程序自动创建
应用程序在启动时会自动检查并创建 Bucket（如果不存在），但需要确保：
- 凭证有创建 Bucket 的权限
- Bucket 名称在 AWS 中全局唯一

---

## 4. 验证连接

### 步骤 4.1: 启动应用程序
```bash
cd EasyCloudDisk/server
mvn clean package -DskipTests
java -jar target/clouddisk-server-*.jar
```

### 步骤 4.2: 检查健康状态
```bash
# 检查服务健康状态（包括 S3 连接）
curl http://localhost:8080/health
```

预期响应：
```json
{
  "status": "UP",
  "components": {
    "s3": {
      "status": "UP"
    }
  }
}
```

### 步骤 4.3: 测试文件上传（可选）
```bash
# 先注册用户
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123456"}'

# 登录获取 Token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123456"}' \
  | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# 创建测试文件
echo "Hello AWS S3!" > test.txt

# 上传文件
curl -X POST http://localhost:8080/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@test.txt" \
  -F "filePath=/test.txt"

# 验证文件是否在 S3 中
aws s3 ls s3://your-bucket-name/
```

---

## 5. 常见问题

### Q1: 连接失败，提示 "Unable to load credentials"
**原因**: 无法找到 AWS 凭证

**解决方法**:
1. 检查环境变量是否设置正确
2. 检查 `~/.aws/credentials` 文件是否存在且格式正确
3. 检查 `application.yml` 中的配置

### Q2: 提示 "Access Denied" 或 "Forbidden"
**原因**: IAM 用户权限不足

**解决方法**:
1. 检查 IAM 用户是否附加了 `AmazonS3FullAccess` 策略
2. 检查 Bucket 的访问策略
3. 确认凭证是否属于正确的 IAM 用户

### Q3: 提示 "Bucket name already exists"
**原因**: S3 Bucket 名称在 AWS 中全局唯一，该名称已被占用

**解决方法**:
1. 选择一个更独特的名称（例如：加上用户名、日期等）
2. 使用 AWS 控制台查看可用的 Bucket 名称

### Q4: 上传文件失败，提示 "Region mismatch"
**原因**: 区域配置不一致

**解决方法**:
1. 确保 `application.yml` 中的 `aws.region` 与 Bucket 所在的区域一致
2. 确保环境变量 `AWS_REGION` 设置正确

### Q5: 在 EC2 实例上如何配置？
**推荐方法**: 使用 IAM 角色（更安全，无需存储凭证）

1. 创建 IAM 角色并附加 S3 访问策略
2. 将角色附加到 EC2 实例
3. 应用程序会自动使用实例角色凭证

**配置步骤**:
```bash
# EC2 实例会自动从元数据服务获取凭证
# 无需配置环境变量或凭证文件
# 只需确保 EC2 实例已附加 IAM 角色
```

### Q6: 如何测试连接（不使用应用程序）？
使用 AWS CLI:
```bash
# 测试凭证
aws sts get-caller-identity

# 列出所有 Bucket
aws s3 ls

# 测试上传/下载
echo "test" > test.txt
aws s3 cp test.txt s3://your-bucket-name/
aws s3 ls s3://your-bucket-name/
aws s3 rm s3://your-bucket-name/test.txt
```

---

## 6. 安全建议

1. **不要提交凭证到 Git**
   - 使用 `.gitignore` 排除 `application.yml`（如果包含凭证）
   - 使用环境变量或 AWS 凭证文件

2. **使用最小权限原则**
   - 生产环境不要使用 `AmazonS3FullAccess`
   - 创建自定义 IAM 策略，仅授予必要的权限

3. **定期轮换访问密钥**
   - 每 90 天更换一次访问密钥
   - 删除不再使用的旧密钥

4. **使用 IAM 角色（EC2/ECS）**
   - 在云环境中使用 IAM 角色而非静态凭证
   - 更安全且无需管理凭证

---

## 7. 参考资源

- [AWS S3 官方文档](https://docs.aws.amazon.com/s3/)
- [AWS IAM 最佳实践](https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html)
- [AWS SDK for Java 2.x 文档](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)
- [AWS CLI 配置指南](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html)

---

**配置完成后，您的应用程序就可以成功连接到 AWS S3 了！** 🎉

