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
            // 计算文件哈希
            showAlert(`正在计算文件哈希: ${file.name}...`, 'info');
            const hash = await hashCalculator.calculateFullFileHash(file);
            
            // 检查是否可以秒传
            const checkResponse = await api.checkQuickUpload(hash);
            
            if (checkResponse.success && checkResponse.data?.canQuickUpload) {
                // 秒传
                this.updateUploadProgress(uploadId, 50);
                const quickResponse = await api.quickUpload(hash, file.name, path);
                
                if (quickResponse.success) {
                    this.updateUploadProgress(uploadId, 100);
                    showAlert(`秒传成功: ${file.name}`, 'success');
                    this.removeUploadItem(uploadId);
                    
                    // 刷新文件列表
                    if (fileManager) {
                        fileManager.loadFiles();
                    }
                    return;
                }
            }

            // 普通上传
            this.updateUploadProgress(uploadId, 10);
            const formData = new FormData();
            formData.append('file', file);
            if (path) {
                formData.append('path', path);
            }

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

    // 加密上传
    async uploadEncryptedFile(file, encryptionKey, path = '/') {
        // TODO: 实现客户端加密
        showAlert('加密上传功能开发中', 'info');
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
        this.uploadEncryptedFile();
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

