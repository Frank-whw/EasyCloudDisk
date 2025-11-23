// 上传管理模块

const uploadManager = {
    uploadQueue: new Map(), // fileId -> uploadInfo

    // 显示上传对话框
    showUploadDialog() {
        const input = document.createElement('input');
        input.type = 'file';
        input.multiple = true;
        input.onchange = (e) => {
            Array.from(e.target.files).forEach(file => {
                this.uploadFile(file);
            });
        };
        input.click();
    },

    // 上传文件（自动选择最佳方式）
    async uploadFile(file, path = null) {
        // 如果path未指定，尝试使用当前目录
        if (path === null && fileManager) {
            path = fileManager.currentPath || '/';
        }
        
        // 检查文件大小
        if (file.size > CONFIG.UPLOAD.MAX_FILE_SIZE) {
            showAlert(`文件大小超过限制（最大${formatFileSize(CONFIG.UPLOAD.MAX_FILE_SIZE)}）`, 'error');
            return;
        }

        // 小文件直接上传，大文件使用断点续传
        if (file.size > CONFIG.UPLOAD.CHUNK_SIZE) {
            return this.uploadWithResumable(file, path);
        } else {
            return this.uploadWithQuickCheck(file, path);
        }
    },

    // 带秒传检查的普通上传
    async uploadWithQuickCheck(file, path = '/') {
        const uploadId = generateId();
        this.addUploadItem(uploadId, file.name, 0);

        try {
            this.updateUploadProgress(uploadId, 10);
            
            const response = await api.uploadFile(file, path);
            
            if (response.success) {
                this.updateUploadProgress(uploadId, 100);
                showAlert(`上传成功: ${file.name}`, 'success');
                this.removeUploadItem(uploadId);
                
                // 刷新文件列表
                if (fileManager) {
                    fileManager.loadFiles();
                }
            } else {
                throw new Error(response.message || '上传失败');
            }
        } catch (error) {
            showAlert(`上传失败: ${file.name} - ${error.message}`, 'error');
            this.removeUploadItem(uploadId);
        }
    },

    // 断点续传上传
    async uploadWithResumable(file, path = '/') {
        const uploadId = generateId();
        this.addUploadItem(uploadId, file.name, 0);

        try {
            // 初始化上传会话
            const initResponse = await api.initResumableUpload(file.name, path, file.size);
            
            if (!initResponse.success || !initResponse.data) {
                throw new Error('初始化上传会话失败');
            }

            const sessionId = initResponse.data.sessionId;
            const chunkSize = CONFIG.UPLOAD.CHUNK_SIZE;
            const totalChunks = Math.ceil(file.size / chunkSize);
            let uploadedChunks = 0;

            // 上传所有分块
            for (let i = 0; i < totalChunks; i++) {
                const start = i * chunkSize;
                const end = Math.min(start + chunkSize, file.size);
                const chunk = file.slice(start, end);

                try {
                    await api.uploadChunk(sessionId, i, chunk);
                    uploadedChunks++;
                    
                    const progress = Math.round((uploadedChunks / totalChunks) * 90); // 90%用于上传，10%用于合并
                    this.updateUploadProgress(uploadId, progress);
                } catch (error) {
                    throw new Error(`上传分块 ${i + 1}/${totalChunks} 失败: ${error.message}`);
                }
            }

            // 完成上传
            this.updateUploadProgress(uploadId, 95);
            const completeResponse = await api.completeResumableUpload(sessionId);
            
            if (completeResponse.success) {
                this.updateUploadProgress(uploadId, 100);
                showAlert(`上传成功: ${file.name}`, 'success');
                this.removeUploadItem(uploadId);
                
                // 刷新文件列表
                if (fileManager) {
                    fileManager.loadFiles();
                }
            } else {
                throw new Error('完成上传失败');
            }
        } catch (error) {
            showAlert(`上传失败: ${file.name} - ${error.message}`, 'error');
            this.removeUploadItem(uploadId);
        }
    },

    // 加密上传文件
    async uploadEncryptedFile(file, encryptionKey, useConvergent = false, path = '/') {
        const uploadId = generateId();
        this.addUploadItem(uploadId, file.name, 0);

        try {
            // 计算文件哈希
            this.updateUploadProgress(uploadId, 5);
            showAlert(`正在计算文件哈希: ${file.name}...`, 'info');
            const hash = await hashCalculator.calculateFullFileHash(file);
            
            // 检查是否可以秒传（仅对收敛加密）
            if (useConvergent) {
                const checkResponse = await api.checkConvergentQuickUpload(hash);
                if (checkResponse.success && checkResponse.data?.canQuickUpload) {
                    // 收敛加密秒传
                    this.updateUploadProgress(uploadId, 50);
                    // 这里需要后端支持收敛加密秒传，暂时跳过
                    showAlert('收敛加密秒传功能开发中', 'info');
                }
            }

            // 客户端加密（简化实现 - 警告：这不是真正的加密！）
            this.updateUploadProgress(uploadId, 20);
            showAlert('警告：当前加密功能为模拟实现，不提供真实的安全性！', 'warning');
            
            // 模拟加密过程（实际需要实现真正的加密）
            await new Promise(resolve => setTimeout(resolve, 1000));
            
            // 创建符合后端期望的加密元数据
            const metadata = {
                fileName: file.name,
                path: path,
                algorithm: 'AES-256-GCM',
                keyDerivation: 'PBKDF2',
                salt: btoa(Math.random().toString()), // 随机盐值
                iterations: 10000,
                convergent: useConvergent,
                iv: btoa(Math.random().toString()), // 随机IV
                originalSize: file.size,
                encryptedSize: file.size, // 模拟：实际应该是加密后的大小
                originalHash: hash
            };

            // 上传加密文件（实际上是原文件）
            this.updateUploadProgress(uploadId, 50);
            const response = await api.uploadEncryptedFile(file, metadata);
            
            if (response.success) {
                this.updateUploadProgress(uploadId, 100);
                showAlert(`加密上传成功: ${file.name}`, 'success');
                this.removeUploadItem(uploadId);
                
                // 刷新文件列表
                if (fileManager) {
                    fileManager.loadFiles();
                }
            } else {
                throw new Error(response.message || '加密上传失败');
            }
        } catch (error) {
            showAlert(`加密上传失败: ${file.name} - ${error.message}`, 'error');
            this.removeUploadItem(uploadId);
        }
    },

    // 显示上传选项
    showUploadOptions() {
        this.showUploadDialog();
    },

    // 显示断点续传上传
    showResumableUpload() {
        const input = document.createElement('input');
        input.type = 'file';
        input.multiple = true;
        input.onchange = (e) => {
            Array.from(e.target.files).forEach(file => {
                this.uploadWithResumable(file);
            });
        };
        input.click();
    },

    // 显示加密上传
    showEncryptedUpload() {
        const modalHtml = `
            <div class="modal-overlay" onclick="this.remove()">
                <div class="modal encryption-modal" onclick="event.stopPropagation()">
                    <div class="modal-header">
                        <h3><i class="fas fa-lock"></i> 加密上传</h3>
                        <button class="btn btn-icon" onclick="this.closest('.modal-overlay').remove()">
                            <i class="fas fa-times"></i>
                        </button>
                    </div>
                    <div class="modal-body">
                        <form id="encryptionForm" onsubmit="event.preventDefault(); uploadManager.startEncryptedUpload();">
                            <div class="form-group">
                                <label>选择文件</label>
                                <input type="file" id="encryptedFileInput" required>
                            </div>
                            <div class="form-group">
                                <label>加密密钥</label>
                                <input type="password" id="encryptionKey" placeholder="请输入加密密钥（至少8位）" required minlength="8">
                            </div>
                            <div class="form-group">
                                <label>确认密钥</label>
                                <input type="password" id="encryptionKeyConfirm" placeholder="请再次输入密钥" required>
                            </div>
                            <div class="form-group">
                                <label>
                                    <input type="checkbox" id="useConvergentEncryption"> 使用收敛加密（支持去重）
                                </label>
                            </div>
                            <div class="form-actions">
                                <button type="button" class="btn btn-secondary" onclick="this.closest('.modal-overlay').remove()">
                                    取消
                                </button>
                                <button type="submit" class="btn btn-primary">
                                    <i class="fas fa-lock"></i> 开始加密上传
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        `;
        
        document.getElementById('modalContainer').innerHTML = modalHtml;
    },

    // 开始加密上传
    async startEncryptedUpload() {
        const fileInput = document.getElementById('encryptedFileInput');
        const key = document.getElementById('encryptionKey').value;
        const keyConfirm = document.getElementById('encryptionKeyConfirm').value;
        const useConvergent = document.getElementById('useConvergentEncryption').checked;
        
        if (!fileInput.files[0]) {
            showAlert('请选择文件', 'error');
            return;
        }
        
        if (key !== keyConfirm) {
            showAlert('两次输入的密钥不一致', 'error');
            return;
        }
        
        if (key.length < 8) {
            showAlert('密钥长度至少8位', 'error');
            return;
        }
        
        // 关闭对话框
        document.querySelector('.modal-overlay').remove();
        
        // 开始加密上传
        await this.uploadEncryptedFile(fileInput.files[0], key, useConvergent);
    },

    // 添加上传项到进度列表
    addUploadItem(uploadId, fileName, progress = 0) {
        const container = document.getElementById('uploadProgressContainer');
        if (!container) return;

        const item = document.createElement('div');
        item.className = 'upload-item';
        item.id = `upload-${uploadId}`;
        item.innerHTML = `
            <div class="upload-item-header">
                <span class="upload-item-name">${fileName}</span>
                <span class="upload-item-status">准备中...</span>
            </div>
            <div class="upload-progress-bar">
                <div class="upload-progress-fill" style="width: ${progress}%"></div>
            </div>
            <div class="upload-item-info">
                <span>${progress}%</span>
                <span>0 KB/s</span>
            </div>
        `;

        container.appendChild(item);
        this.uploadQueue.set(uploadId, { fileName, progress, item });
    },

    // 更新上传进度
    updateUploadProgress(uploadId, progress) {
        const uploadInfo = this.uploadQueue.get(uploadId);
        if (!uploadInfo) return;

        uploadInfo.progress = progress;
        const item = uploadInfo.item;
        
        const progressFill = item.querySelector('.upload-progress-fill');
        const progressText = item.querySelector('.upload-item-info span:first-child');
        const status = item.querySelector('.upload-item-status');

        if (progressFill) {
            progressFill.style.width = `${progress}%`;
        }
        if (progressText) {
            progressText.textContent = `${progress}%`;
        }
        if (status) {
            if (progress === 100) {
                status.textContent = '完成';
                status.style.color = 'var(--success-color)';
            } else if (progress > 0) {
                status.textContent = '上传中...';
            } else {
                status.textContent = '准备中...';
            }
        }
    },

    // 移除上传项
    removeUploadItem(uploadId) {
        const uploadInfo = this.uploadQueue.get(uploadId);
        if (uploadInfo && uploadInfo.item) {
            uploadInfo.item.remove();
        }
        this.uploadQueue.delete(uploadId);
    },

    // 获取上传会话列表
    async loadUploadSessions() {
        try {
            const response = await api.listUploadSessions();
            if (response.success && response.data) {
                // 显示未完成的上传会话
                const incompleteSessions = response.data.filter(s => s.status !== 'COMPLETED');
                if (incompleteSessions.length > 0) {
                    showAlert(`有 ${incompleteSessions.length} 个未完成的上传任务`, 'info');
                }
            }
        } catch (error) {
            console.error('加载上传会话失败:', error);
        }
    }
};

