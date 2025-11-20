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
                try {
                    const quickResponse = await api.quickUpload(hash, file.name, path);
                    
                    if (quickResponse.success) {
                        this.updateUploadProgress(uploadId, 100);
                        showAlert(`秒传成功: ${file.name}`, 'success');
                        this.removeUploadItem(uploadId);
                        
                        // 刷新文件列表
                        if (fileManager && typeof fileManager.loadFiles === 'function') {
                            fileManager.loadFiles();
                        }
                        return;
                    }
                } catch (error) {
                    // 如果秒传失败（如文件已存在），降级为普通上传
                    if (error.message && error.message.includes('已存在')) {
                        showAlert(`文件已存在，将重新上传: ${file.name}`, 'info');
                        // 继续执行普通上传流程
                    } else {
                        throw error;
                    }
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
                // 小文件也会进行块级去重和压缩（如果文件大于4MB会被切分）
                if (file.size > 4 * 1024 * 1024) {
                    showAlert(`上传成功: ${file.name} (已进行块级去重和压缩，详情请查看后端日志)`, 'success');
                } else {
                    showAlert(`上传成功: ${file.name}`, 'success');
                }
                this.removeUploadItem(uploadId);
                
            // 刷新文件列表
                    if (fileManager && typeof fileManager.loadFiles === 'function') {
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

    // 断点续传上传（带秒传检查）
    async uploadWithResumable(file, path = '/') {
        const uploadId = generateId();
        this.addUploadItem(uploadId, file.name, 0);

        try {
            // 计算文件哈希（用于秒传检查）
            showAlert(`正在计算文件哈希: ${file.name}...`, 'info');
            const hash = await hashCalculator.calculateFullFileHash(file);
            
            // 检查是否可以秒传
            const checkResponse = await api.checkQuickUpload(hash);
            
            if (checkResponse.success && checkResponse.data?.canQuickUpload) {
                // 秒传
                this.updateUploadProgress(uploadId, 50);
                try {
                    const quickResponse = await api.quickUpload(hash, file.name, path);
                    
                    if (quickResponse.success) {
                        this.updateUploadProgress(uploadId, 100);
                        showAlert(`秒传成功: ${file.name}`, 'success');
                        this.removeUploadItem(uploadId);
                        
                        // 刷新文件列表
                        if (fileManager && typeof fileManager.loadFiles === 'function') {
                            fileManager.loadFiles();
                        }
                        return;
                    }
                } catch (error) {
                    // 如果秒传失败（如文件已存在），继续断点续传流程
                    if (error.message && error.message.includes('已存在')) {
                        showAlert(`文件已存在，将重新上传: ${file.name}`, 'info');
                    } else {
                        console.warn('秒传失败，继续断点续传:', error);
                    }
                }
            }

            // 初始化上传会话
            this.updateUploadProgress(uploadId, 5);
            const initResponse = await api.initResumableUpload(file.name, path, file.size);
            
            if (!initResponse.success || !initResponse.data) {
                throw new Error('初始化上传会话失败');
            }

            const sessionId = initResponse.data.sessionId;
            const chunkSize = CONFIG.UPLOAD.CHUNK_SIZE;
            const totalChunks = Math.ceil(file.size / chunkSize);
            let uploadedChunks = 0;

            // 显示分块上传信息
            showAlert(`开始分块上传: ${file.name} (共${totalChunks}个分块，每块${formatFileSize(chunkSize)})`, 'info');

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
                    
                    // 更新状态显示
                    const uploadInfo = this.uploadQueue.get(uploadId);
                    if (uploadInfo && uploadInfo.item) {
                        const status = uploadInfo.item.querySelector('.upload-item-status');
                        if (status) {
                            status.textContent = `上传中... (${uploadedChunks}/${totalChunks} 分块)`;
                        }
                    }
                } catch (error) {
                    throw new Error(`上传分块 ${i + 1}/${totalChunks} 失败: ${error.message}`);
                }
            }

            // 完成上传（后端会进行块级去重和压缩）
            this.updateUploadProgress(uploadId, 95);
            const uploadInfo = this.uploadQueue.get(uploadId);
            if (uploadInfo && uploadInfo.item) {
                const status = uploadInfo.item.querySelector('.upload-item-status');
                if (status) {
                    status.textContent = '处理中... (块级去重和压缩)';
                }
            }
            
            const completeResponse = await api.completeResumableUpload(sessionId);
            
            if (completeResponse.success) {
                this.updateUploadProgress(uploadId, 100);
                showAlert(`上传成功: ${file.name} (已进行块级去重和压缩，详情请查看后端日志)`, 'success');
                this.removeUploadItem(uploadId);
                
            // 刷新文件列表
                    if (fileManager && typeof fileManager.loadFiles === 'function') {
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
    async uploadEncryptedFile(file, password, path = null, options = {}) {
        // 如果path未指定，尝试使用当前目录
        if (path === null && fileManager) {
            path = fileManager.currentPath || '/';
        }

        const uploadId = generateId();
        this.addUploadItem(uploadId, file.name, 0);

        try {
            showAlert(`正在加密文件: ${file.name}...`, 'info');
            this.updateUploadProgress(uploadId, 10);

            // 1. 加密文件
            const { encryptedFile, metadata } = await encryptionManager.encryptFile(
                file,
                password,
                options
            );

            this.updateUploadProgress(uploadId, 50);

            // 2. 如果是收敛加密，检查是否可以秒传
            if (metadata.convergent) {
                const canQuickUpload = await encryptionManager.checkConvergentQuickUpload(
                    metadata.originalHash
                );
                if (canQuickUpload) {
                    // 收敛加密秒传（需要后端支持）
                    showAlert('收敛加密文件已存在，秒传功能开发中', 'info');
                    // TODO: 实现收敛加密秒传
                }
            }

            // 3. 上传加密文件
            this.updateUploadProgress(uploadId, 60);
            const response = await api.uploadEncryptedFile(encryptedFile, {
                ...metadata,
                path: path || '/'
            });

            if (response.success) {
                this.updateUploadProgress(uploadId, 100);
                showAlert(`加密文件上传成功: ${file.name}`, 'success');
                this.removeUploadItem(uploadId);

                // 刷新文件列表
                if (fileManager && typeof fileManager.loadFiles === 'function') {
                    fileManager.loadFiles();
                }
                return response;
            } else {
                throw new Error(response.message || '上传失败');
            }
        } catch (error) {
            showAlert(`加密上传失败: ${file.name} - ${error.message}`, 'error');
            this.removeUploadItem(uploadId);
            throw error;
        }
    },

    // 显示加密上传对话框
    async showEncryptedUploadDialog() {
        const content = `
            <div class="form-group">
                <label>选择文件</label>
                <input type="file" id="encryptedFileInput" style="width: 100%;">
            </div>
            <div class="form-group">
                <label>加密密码</label>
                <input type="password" id="encryptedPasswordInput" placeholder="请输入加密密码" autofocus>
            </div>
            <div class="form-group">
                <label>加密算法</label>
                <select id="encryptedAlgorithmSelect" style="width: 100%; padding: 8px;">
                    <option value="AES-256-GCM">AES-256-GCM（推荐）</option>
                    <option value="AES-256-CBC">AES-256-CBC</option>
                </select>
            </div>
            <div class="form-group">
                <label>
                    <input type="checkbox" id="convergentEncryptionCheck">
                    使用收敛加密（支持去重，但安全性较低）
                </label>
            </div>
        `;

        const footer = `
            <button class="btn btn-secondary" onclick="closeModal(this.closest('.modal-overlay'))">取消</button>
            <button class="btn btn-primary" onclick="
                const fileInput = document.getElementById('encryptedFileInput');
                const passwordInput = document.getElementById('encryptedPasswordInput');
                const algorithmSelect = document.getElementById('encryptedAlgorithmSelect');
                const convergentCheck = document.getElementById('convergentEncryptionCheck');
                
                if (!fileInput.files[0]) {
                    showAlert('请选择文件', 'error');
                    return;
                }
                if (!passwordInput.value) {
                    showAlert('请输入加密密码', 'error');
                    return;
                }
                
                const file = fileInput.files[0];
                const password = passwordInput.value;
                const algorithm = algorithmSelect.value;
                const convergent = convergentCheck.checked;
                
                closeModal(this.closest('.modal-overlay'));
                uploadManager.uploadEncryptedFile(file, password, null, {
                    algorithm: algorithm,
                    convergent: convergent
                });
            ">上传</button>
        `;

        createModal('加密上传', content, footer);
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
        this.showEncryptedUploadDialog();
    },

    // 差分同步更新文件
    async syncFileWithDelta(fileId, newFile) {
        try {
            return await deltaSyncManager.syncFile(fileId, newFile);
        } catch (error) {
            showAlert(`差分同步失败: ${error.message}`, 'error');
            throw error;
        }
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

