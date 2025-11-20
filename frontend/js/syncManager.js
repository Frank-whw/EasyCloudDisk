// 文件同步管理模块（SSE）

const syncManager = {
    reader: null,
    reconnectAttempts: 0,
    maxReconnectAttempts: 5,
    reconnectDelay: 3000,
    isRunning: false,

    // 启动同步
    start() {
        if (!auth.isAuthenticated()) {
            return;
        }

        this.isRunning = true;
        this.connect();
    },

    // 连接SSE（使用fetch实现，支持自定义headers）
    async connect() {
        if (!this.isRunning) {
            return;
        }

        try {
            const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
            if (!token) {
                return;
            }

            // 使用fetch实现SSE（支持Bearer token认证）
            const url = `${CONFIG.API_BASE_URL}/files/sync`;
            const response = await fetch(url, {
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Accept': 'text/event-stream'
                }
            });

            if (!response.ok) {
                throw new Error('同步连接失败');
            }

            console.log('同步连接已建立');
            this.reconnectAttempts = 0;
            showAlert('文件同步已连接', 'success');

            this.reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';

            // 持续读取流数据
            const readStream = async () => {
                try {
                    while (this.isRunning) {
                        const { done, value } = await this.reader.read();
                        
                        if (done) {
                            console.log('同步连接已关闭');
                            if (this.isRunning) {
                                this.handleReconnect();
                            }
                            break;
                        }

                        buffer += decoder.decode(value, { stream: true });
                        const lines = buffer.split('\n');
                        buffer = lines.pop() || ''; // 保留最后不完整的行

                        for (const line of lines) {
                            if (line.startsWith('data: ')) {
                                try {
                                    const data = JSON.parse(line.substring(6));
                                    this.handleSyncEvent(data);
                                } catch (error) {
                                    console.error('解析同步事件失败:', error);
                                }
                            }
                        }
                    }
                } catch (error) {
                    console.error('读取同步流失败:', error);
                    if (this.isRunning) {
                        this.handleReconnect();
                    }
                }
            };

            readStream();
        } catch (error) {
            console.error('启动同步失败:', error);
            if (this.isRunning) {
                this.handleReconnect();
            }
        }
    },

    // 处理重连
    handleReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
            setTimeout(() => {
                this.connect();
            }, this.reconnectDelay);
        } else {
            showAlert('文件同步连接失败，请刷新页面重试', 'error');
        }
    },

    // 处理同步事件
    handleSyncEvent(data) {
        const { type, fileId, path, name } = data;

        switch (type) {
            case 'upload':
            case 'quick-upload':
            case 'encrypted-upload':
                showAlert(`文件已同步: ${name || '新文件'}`, 'success');
                if (fileManager) {
                    fileManager.loadFiles();
                }
                break;

            case 'delete':
                showAlert('文件已删除', 'info');
                if (fileManager) {
                    fileManager.loadFiles();
                }
                break;

            case 'mkdir':
                showAlert(`目录已创建: ${name}`, 'success');
                if (fileManager) {
                    fileManager.loadFiles();
                }
                break;

            case 'delta-update':
                showAlert('文件已更新', 'info');
                if (fileManager) {
                    fileManager.loadFiles();
                }
                break;

            default:
                console.log('未知同步事件:', data);
        }
    },

    // 停止同步
    stop() {
        this.isRunning = false;
        if (this.reader) {
            this.reader.cancel();
            this.reader = null;
        }
        this.reconnectAttempts = 0;
    },

    // 检查同步状态
    isConnected() {
        return this.isRunning && this.reconnectAttempts < this.maxReconnectAttempts;
    }
};

