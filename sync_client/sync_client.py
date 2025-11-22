import os
import sys
import time
import json
import logging
import requests
import threading
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler
import sseclient

# 配置
SERVER_URL = "http://localhost:8080"
SYNC_DIR = "./sync_folder"
EMAIL = "1693698849@qq.com"
PASSWORD = "abc123456"  # 该用户存在

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
logger = logging.getLogger(__name__)

class CloudDiskClient:
    def __init__(self, server_url, email, password):
        self.server_url = server_url
        self.email = email
        self.password = password
        self.token = None
        self.user_id = None

    def login(self):
        """登录获取Token"""
        try:
            response = requests.post(f"{self.server_url}/auth/login", json={
                "email": self.email,
                "password": self.password
            })
            if response.status_code == 200:
                data = response.json().get("data", {})
                self.token = data.get("token")
                self.user_id = data.get("userId")
                logger.info(f"登录成功: {self.email}")
                return True
            else:
                # 如果是500错误，可能是因为用户不存在导致后端抛出未捕获异常
                # 尝试注册
                if response.status_code == 500 or (response.status_code == 400 and "USER_NOT_FOUND" in response.text):
                    logger.warning("登录失败，尝试自动注册...")
                    if self.register():
                        return self.login() # 注册成功后重试登录
                
                logger.error(f"登录失败: {response.text}")
                return False
        except Exception as e:
            logger.error(f"连接服务器失败: {e}")
            return False

    def register(self):
        """注册用户"""
        try:
            response = requests.post(f"{self.server_url}/auth/register", json={
                "email": self.email,
                "password": self.password
            })
            if response.status_code == 200:
                logger.info(f"注册成功: {self.email}")
                return True
            elif "EMAIL_EXISTS" in response.text:
                logger.info("用户已存在")
                return True # 视为成功，继续尝试登录
            else:
                logger.error(f"注册失败: {response.text}")
                return False
        except Exception as e:
            logger.error(f"注册异常: {e}")
            return False

    def get_headers(self):
        return {"Authorization": f"Bearer {self.token}"}

    def upload_file(self, local_path, remote_path):
        """上传文件"""
        if not self.token: return
        try:
            relative_path = os.path.dirname(remote_path).replace("\\", "/")
            if not relative_path: relative_path = "/"
            if not relative_path.startswith("/"): relative_path = "/" + relative_path
            
            files = {'file': open(local_path, 'rb')}
            data = {'path': relative_path}
            
            logger.info(f"正在上传: {local_path} -> {relative_path}")
            resp = requests.post(
                f"{self.server_url}/files/upload",
                headers=self.get_headers(),
                files=files,
                data=data
            )
            if resp.status_code == 200:
                logger.info("上传成功")
            else:
                logger.error(f"上传失败: {resp.text}")
        except Exception as e:
            logger.error(f"上传异常: {e}")

    def delete_file(self, file_id):
        """删除文件（需要先根据路径查询ID，这里简化处理，实际需维护本地路径到ID的映射）"""
        # 由于API删除需要FileID，单纯靠路径很难直接删除，除非先查询。
        # 为简化演示，本脚本主要演示监控和上传。
        logger.warning("暂未实现按路径自动删除远程文件（需要维护路径-ID映射）")
        pass

    def create_directory(self, path):
        """创建目录"""
        if not self.token: return
        # 简化处理：解析父目录和新目录名
        path = path.replace("\\", "/")
        if path.endswith("/"): path = path[:-1]
        parent = os.path.dirname(path)
        name = os.path.basename(path)
        if not parent.startswith("/"): parent = "/" + parent
        
        try:
            logger.info(f"创建目录: {name} at {parent}")
            resp = requests.post(
                f"{self.server_url}/files/directories",
                headers=self.get_headers(),
                json={"path": parent, "name": name}
            )
            if resp.status_code == 200:
                logger.info("目录创建成功")
            else:
                logger.error(f"目录创建失败: {resp.text}")
        except Exception as e:
            logger.error(f"创建目录异常: {e}")

class SyncHandler(FileSystemEventHandler):
    def __init__(self, client, base_path):
        self.client = client
        self.base_path = os.path.abspath(base_path)

    def _get_relative_path(self, abs_path):
        return "/" + os.path.relpath(abs_path, self.base_path).replace("\\", "/")

    def on_created(self, event):
        if event.is_directory:
            rel_path = self._get_relative_path(event.src_path)
            self.client.create_directory(rel_path)
        else:
            rel_path = self._get_relative_path(event.src_path)
            self.client.upload_file(event.src_path, rel_path)

    def on_modified(self, event):
        if not event.is_directory:
            rel_path = self._get_relative_path(event.src_path)
            self.client.upload_file(event.src_path, rel_path)

    def on_moved(self, event):
        logger.info(f"移动/重命名: {event.src_path} -> {event.dest_path}")
        # 简化处理：视为新文件上传
        if not event.is_directory:
            rel_path = self._get_relative_path(event.dest_path)
            self.client.upload_file(event.dest_path, rel_path)

    def on_deleted(self, event):
        logger.info(f"本地删除: {event.src_path}")
        # 需要查询ID才能删除，暂略

def listen_server_events(client):
    """监听服务器SSE事件"""
    url = f"{client.server_url}/files/sync"
    headers = client.get_headers()
    
    logger.info("开始监听服务器变更...")
    try:
        response = requests.get(url, stream=True, headers=headers)
        client = sseclient.SSEClient(response)
        for event in client.events():
            try:
                data = json.loads(event.data)
                logger.info(f"收到服务器通知: {data}")
                # 这里可以添加逻辑：如果变更来自其他客户端，则下载文件到本地
            except:
                pass
    except Exception as e:
        logger.error(f"SSE连接断开: {e}")

def main():
    if not os.path.exists(SYNC_DIR):
        os.makedirs(SYNC_DIR)
    
    client = CloudDiskClient(SERVER_URL, EMAIL, PASSWORD)
    if not client.login():
        return

    # 启动SSE监听线程
    t = threading.Thread(target=listen_server_events, args=(client,))
    t.daemon = True
    t.start()

    # 启动本地监控
    event_handler = SyncHandler(client, SYNC_DIR)
    observer = Observer()
    observer.schedule(event_handler, SYNC_DIR, recursive=True)
    observer.start()
    
    logger.info(f"正在监控文件夹: {os.path.abspath(SYNC_DIR)}")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        observer.stop()
    observer.join()

if __name__ == "__main__":
    main()

