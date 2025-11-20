# 快速启动指南

## 场景1：在EC2上运行（生产环境，推荐）

你的后端服务应该运行在EC2上，因为：
- ✅ EC2已经配置了AWS凭证
- ✅ 服务已经部署在 `54.95.61.230:8080`
- ✅ 前端配置的API地址指向EC2

### 在EC2上启动步骤：

```bash
# 1. SSH连接到EC2（你已经连接了）
ssh myec2
# 现在你在 ubuntu@ip-172-31-1-201:~$

# 2. 进入项目目录（如果代码在EC2上）
cd ~/clouddisk
# 或者如果代码在 /home/ubuntu/clouddisk

# 3. 检查服务是否已经在运行
curl http://localhost:8080/health

# 4. 如果服务未运行，启动服务
cd server
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"
# AWS凭证已经在EC2上通过aws configure配置好了，不需要再设置

# 5. 使用启动脚本启动
chmod +x scripts/server-startup.sh
./scripts/server-startup.sh start

# 或者直接使用Maven（如果代码在EC2上）
mvn spring-boot:run
```

## 场景2：在本地WSL运行（开发测试）

如果你想在本地开发测试：

```bash
# 1. 在本地WSL环境
cd /mnt/d/2025.2/Cloud_Computing/Project/EasyCloudDisk/server

# 2. 设置环境变量（需要你的AWS凭证）
export AWS_ACCESS_KEY_ID="AKIARCSPQ2MSDC2UES4A"  # 从EC2上获取
export AWS_SECRET_ACCESS_KEY="your-secret-key"   # 需要你的secret key
export AWS_REGION="ap-northeast-1"
export AWS_S3_BUCKET="clouddisk-test-1762861672"

# 3. 启动服务
chmod +x start.sh
./start.sh
# 或
mvn spring-boot:run
```

## 推荐方案

**建议在EC2上运行**，因为：
1. ✅ AWS凭证已经配置好
2. ✅ 服务地址固定（54.95.61.230:8080）
3. ✅ 前端已经配置好连接EC2
4. ✅ 生产环境更稳定

## 检查服务状态

```bash
# 在EC2上检查
curl http://localhost:8080/health

# 从本地检查EC2服务
curl http://54.95.61.230:8080/health
```

## 如果服务已经在运行

如果EC2上的服务已经在运行，你只需要：
1. 确认服务正常：`curl http://54.95.61.230:8080/health`
2. 启动前端应用
3. 开始使用

