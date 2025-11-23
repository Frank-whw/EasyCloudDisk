// 文件管理模块

const fileManager = {
    currentPath: '/',
    viewMode: CONFIG.VIEW_MODES.GRID,
    files: [],
    selectedFiles: new Set(),

    // 初始化
    init() {
        this.setupDragAndDrop();
        this.setupSearch();
    },

    // 设置拖拽上传
    setupDragAndDrop() {
        const content = document.querySelector('.content');
        
        content.addEventListener('dragover', (e) => {
            e.preventDefault();
            content.classList.add('drag-over');
        });

        content.addEventListener('dragleave', (e) => {
            e.preventDefault();
            content.classList.remove('drag-over');
        });

        content.addEventListener('drop', (e) => {
            e.preventDefault();
            content.classList.remove('drag-over');
            
            const files = Array.from(e.dataTransfer.files);
            files.forEach(file => {
                uploadManager.uploadFile(file, this.currentPath);
            });
        });
    },

    // 设置搜索
    setupSearch() {
        const searchInput = document.getElementById('searchInput');
        if (searchInput) {
            searchInput.addEventListener('input', debounce((e) => {
                this.searchFiles(e.target.value);
            }, 300));
        }
    },

    // 加载文件列表
    async loadFiles(path = null) {
        try {
            showLoading();
            const targetPath = path !== null ? path : this.currentPath;
            const response = await api.listFiles(targetPath);
            
            if (response.success) {
                this.files = response.data || [];
                this.currentPath = targetPath;
                this.renderBreadcrumb();
                this.renderFiles();
            } else {
                showAlert('加载文件列表失败: ' + (response.message || '未知错误'), 'error');
            }
        } catch (error) {
            showAlert('加载文件列表失败: ' + error.message, 'error');
        } finally {
            hideLoading();
        }
    },

    // 渲染文件列表
    renderFiles() {
        const container = document.getElementById('fileListContainer');
        if (!container) return;

        if (this.files.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <i class="fas fa-cloud-upload-alt"></i>
                    <p>当前目录为空，拖拽文件到此处上传</p>
                </div>
            `;
            return;
        }

        if (this.viewMode === CONFIG.VIEW_MODES.GRID) {
            this.renderGridView(container);
        } else {
            this.renderListView(container);
        }
    },

    // 渲染网格视图
    renderGridView(container) {
        container.innerHTML = '<div class="file-list-grid"></div>';
        const grid = container.querySelector('.file-list-grid');

        this.files.forEach(file => {
            const fileItem = this.createFileItem(file, 'grid');
            grid.appendChild(fileItem);
        });
    },

    // 渲染列表视图
    renderListView(container) {
        container.innerHTML = '<div class="file-list-list"></div>';
        const list = container.querySelector('.file-list-list');

        this.files.forEach(file => {
            const fileItem = this.createFileItem(file, 'list');
            list.appendChild(fileItem);
        });
    },

    // 创建文件项
    createFileItem(file, mode) {
        const item = document.createElement('div');
        item.className = `file-item file-item-${mode}`;
        item.dataset.fileId = file.fileId;

        // 如果是目录，使用文件夹图标；否则根据文件扩展名获取图标
        const icon = file.directory ? 'fas fa-folder' : getFileIcon(file.name);
        const size = formatFileSize(file.size || 0);
        const date = formatDate(file.createdAt || file.updatedAt);

        if (mode === 'grid') {
            item.innerHTML = `
                <div class="file-icon">
                    <i class="${icon}"></i>
                </div>
                <div class="file-name" title="${file.name}">${file.name}</div>
                <div class="file-meta">${size} · ${date}</div>
                <div class="file-actions">
                    ${!file.directory ? `
                    <button class="btn btn-primary btn-small" onclick="fileManager.downloadFile('${file.fileId}', '${file.name}')">
                        <i class="fas fa-download"></i> 下载
                    </button>
                    <button class="btn btn-secondary btn-small" onclick="fileManager.showVersions('${file.fileId}', '${file.name}')">
                        <i class="fas fa-history"></i> 版本
                    </button>
                    <button class="btn btn-secondary btn-small" onclick="shareManager.showShareDialog('${file.fileId}', '${file.name}')" title="创建分享链接">
                        <i class="fas fa-share-alt"></i> 分享
                    </button>
                    <button class="btn btn-danger btn-small" onclick="fileManager.deleteFile('${file.fileId}')">
                        <i class="fas fa-trash"></i> 删除
                    </button>` : `
                    <button class="btn btn-danger btn-small" onclick="fileManager.deleteFile('${file.fileId}')">
                        <i class="fas fa-trash"></i> 删除
                    </button>`}
                </div>
            `;
        } else {
            item.innerHTML = `
                <div class="file-icon">
                    <i class="${icon}"></i>
                </div>
                <div class="file-info">
                    <div class="file-name" title="${file.name}">${file.name}</div>
                    <div class="file-meta">${size} · ${date}</div>
                </div>
                <div class="file-actions">
                    ${!file.directory ? `
                    <button class="btn btn-primary btn-small" onclick="fileManager.downloadFile('${file.fileId}', '${file.name}')">
                        <i class="fas fa-download"></i> 下载
                    </button>
                    <button class="btn btn-secondary btn-small" onclick="fileManager.showVersions('${file.fileId}', '${file.name}')">
                        <i class="fas fa-history"></i> 版本
                    </button>
                    <button class="btn btn-secondary btn-small" onclick="shareManager.showShareDialog('${file.fileId}', '${file.name}')" title="创建分享链接">
                        <i class="fas fa-share-alt"></i> 分享
                    </button>
                    <button class="btn btn-danger btn-small" onclick="fileManager.deleteFile('${file.fileId}')">
                        <i class="fas fa-trash"></i> 删除
                    </button>` : `
                    <button class="btn btn-danger btn-small" onclick="fileManager.deleteFile('${file.fileId}')">
                        <i class="fas fa-trash"></i> 删除
                    </button>`}
                </div>
            `;
        }

        // 处理文件/文件夹点击事件
        if (file.directory) {
            // 文件夹：单击进入目录
            item.style.cursor = 'pointer';
            item.addEventListener('click', (e) => {
                // 如果点击的是按钮，不触发进入目录
                if (e.target.closest('.file-actions') || e.target.closest('button')) {
                    return;
                }
                e.preventDefault();
                e.stopPropagation();
                
                // 调试日志
                console.log('Entering directory:', file);
                
                // 直接使用后端返回的完整路径
                const targetPath = file.path;
                
                console.log('Target path:', targetPath);
                this.enterDirectory(targetPath);
            });
        } else {
            // 文件：双击下载
            item.addEventListener('dblclick', (e) => {
                e.preventDefault();
                e.stopPropagation();
                this.downloadFile(file.fileId, file.name);
            });
        }

        return item;
    },

    // 下载文件
    async downloadFile(fileId, fileName) {
        try {
            showLoading('正在准备下载...');
            console.log('开始下载文件:', fileId, fileName);

            // 检查是否是加密文件
            try {
                const encryptionResponse = await api.getEncryptionMetadata(fileId);
                if (encryptionResponse.success && encryptionResponse.data?.encrypted) {
                    console.log('检测到加密文件:', encryptionResponse.data);
                    showAlert('正在下载加密文件，请稍候...', 'info');
                }
            } catch (e) {
                console.log('文件加密状态检查失败:', e);
            }

            // 设置超时
            const controller = new AbortController();
            const timeoutId = setTimeout(() => {
                controller.abort();
                showAlert('下载超时，请重试', 'error');
                hideLoading();
            }, 60000); // 60秒超时，加密文件可能需要更长时间

            const response = await fetch(`${CONFIG.API_BASE_URL}/files/${fileId}/download`, {
                method: 'GET',
                headers: {
                    'Authorization': `Bearer ${localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN)}`
                },
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                const errorText = await response.text();
                console.error('下载失败:', response.status, errorText);
                showAlert(`下载失败: ${response.status} ${errorText}`, 'error');
                return;
            }

            showLoading('正在下载文件数据...');
            console.log('开始接收文件数据...');

            const contentLength = response.headers.get('content-length');
            const totalSize = contentLength ? parseInt(contentLength) : 0;
            console.log('文件大小:', totalSize, 'bytes');

            if (totalSize > 100 * 1024 * 1024) { // 大于100MB的文件
                showAlert('大文件下载中，请耐心等待...', 'info');
            }

            const blob = await response.blob();
            console.log('文件数据接收完成，大小:', blob.size, '实际接收大小:', blob.size);

            // 验证blob大小
            if (blob.size === 0) {
                showAlert('下载的文件为空，请检查文件是否存在', 'error');
                return;
            }

            // 创建下载链接
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = fileName;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);

            showAlert('下载成功！', 'success');
        } catch (error) {
            console.error('下载过程中出错:', error);
            if (error.name === 'AbortError') {
                showAlert('下载已取消或超时', 'warning');
            } else {
                showAlert('下载失败: ' + error.message, 'error');
            }
        } finally {
            hideLoading();
        }
    },

    // 删除文件
    async deleteFile(fileId) {
        // 查找文件信息以确定是文件还是文件夹
        const file = this.files.find(f => f.fileId === fileId);
        const isDirectory = file ? file.directory : false;
        const itemName = file ? file.name : '该项目';
        
        const confirmMessage = isDirectory 
            ? `确定要删除文件夹 "${itemName}" 吗？此操作不可恢复。`
            : '确定要删除这个文件吗？此操作不可恢复。';

        if (!confirm(confirmMessage)) {
            return;
        }

        try {
            const response = await api.deleteFile(fileId);

            if (response.success) {
                showAlert('删除成功！', 'success');
                this.loadFiles();
            } else {
                showAlert(response.message || '删除失败', 'error');
            }
        } catch (error) {
            showAlert('删除失败: ' + error.message, 'error');
        }
    },

    // 创建目录
    async createDirectory(name) {
        if (!name || !name.trim()) {
            showAlert('请输入目录名称', 'error');
            return;
        }

        try {
            const response = await api.createDirectory(this.currentPath, name.trim());

            if (response.success) {
                showAlert('目录创建成功！', 'success');
                this.loadFiles();
            } else {
                showAlert(response.message || '目录创建失败', 'error');
            }
        } catch (error) {
            showAlert('目录创建失败: ' + error.message, 'error');
        }
    },

    // 显示创建目录对话框
    showCreateDirectoryDialog() {
        const content = `
            <div class="form-group">
                <label>目录名称</label>
                <input type="text" id="directoryNameInput" placeholder="请输入目录名称" autofocus>
            </div>
        `;

        const footer = `
            <button class="btn btn-secondary" onclick="closeModal(this.closest('.modal-overlay'))">取消</button>
            <button class="btn btn-primary" onclick="
                const name = document.getElementById('directoryNameInput').value;
                closeModal(this.closest('.modal-overlay'));
                fileManager.createDirectory(name);
            ">创建</button>
        `;

        const modal = createModal('新建文件夹', content, footer);
        
        // 回车创建
        const input = modal.querySelector('#directoryNameInput');
        input.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                const name = input.value;
                closeModal(modal);
                this.createDirectory(name);
            }
        });
    },

    // 设置视图模式
    setViewMode(mode) {
        this.viewMode = mode;
        localStorage.setItem('viewMode', mode);

        const buttons = document.querySelectorAll('.view-toggle .btn-icon');
        buttons.forEach(btn => btn.classList.remove('active'));
        const activeIndex = mode === CONFIG.VIEW_MODES.GRID ? 0 : 1;
        if (buttons[activeIndex]) {
            buttons[activeIndex].classList.add('active');
        }

        this.renderFiles();
    },

    // 刷新
    refresh() {
        this.loadFiles();
    },

    // 搜索文件
    searchFiles(keyword) {
        if (!keyword) {
            this.renderFiles();
            return;
        }

        const filtered = this.files.filter(file => 
            file.name.toLowerCase().includes(keyword.toLowerCase())
        );

        const container = document.getElementById('fileListContainer');
        if (filtered.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <i class="fas fa-search"></i>
                    <p>未找到匹配的文件</p>
                </div>
            `;
            return;
        }

        // 临时保存原始文件列表
        const originalFiles = this.files;
        this.files = filtered;
        this.renderFiles();
        this.files = originalFiles;
    },

    // 显示所有文件
    showAllFiles() {
        this.enterDirectory('/');
    },

    // 进入指定目录
    enterDirectory(directoryPath) {
        // 标准化路径
        let normalizedPath = directoryPath.replace(/\\/g, '/');
        if (!normalizedPath.startsWith('/')) {
            normalizedPath = '/' + normalizedPath;
        }
        if (normalizedPath.endsWith('/') && normalizedPath.length > 1) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length - 1);
        }
        this.currentPath = normalizedPath;
        this.loadFiles(normalizedPath);
    },

    // 返回上级目录
    goToParentDirectory() {
        if (this.currentPath === '/') {
            return; // 已在根目录
        }
        const pathParts = this.currentPath.split('/').filter(p => p);
        pathParts.pop();
        const parentPath = pathParts.length > 0 ? '/' + pathParts.join('/') : '/';
        this.enterDirectory(parentPath);
    },

    // 渲染面包屑导航
    renderBreadcrumb() {
        const container = document.getElementById('breadcrumbContainer');
        if (!container) return;

        const pathParts = this.currentPath.split('/').filter(p => p);
        let breadcrumbHTML = '<div class="breadcrumb">';
        
        // 根目录
        breadcrumbHTML += `<span class="breadcrumb-item ${this.currentPath === '/' ? 'active' : ''}" onclick="fileManager.enterDirectory('/')">
            <i class="fas fa-home"></i> 根目录
        </span>`;

        // 路径部分
        let currentPath = '';
        pathParts.forEach((part, index) => {
            currentPath += '/' + part;
            const isLast = index === pathParts.length - 1;
            breadcrumbHTML += `<span class="breadcrumb-separator"><i class="fas fa-chevron-right"></i></span>`;
            breadcrumbHTML += `<span class="breadcrumb-item ${isLast ? 'active' : ''}" onclick="fileManager.enterDirectory('${currentPath}')">
                ${part}
            </span>`;
        });

        breadcrumbHTML += '</div>';

        // 添加上级目录按钮
        if (this.currentPath !== '/') {
            breadcrumbHTML += `<button class="btn btn-secondary btn-small" onclick="fileManager.goToParentDirectory()" title="返回上级目录">
                <i class="fas fa-arrow-up"></i> 返回上级
            </button>`;
        }

        container.innerHTML = breadcrumbHTML;
    },

    // 显示最近使用的文件
    showRecentFiles() {
        // TODO: 实现最近使用文件功能
        showAlert('最近使用功能开发中', 'info');
    },

    // 显示文件版本历史
    async showVersions(fileId, fileName) {
        try {
            showLoading('加载版本历史...');
            const response = await api.getFileVersions(fileId);

            if (response.success) {
                this.renderVersionsDialog(fileId, fileName, response.data || []);
            } else {
                showAlert('加载版本历史失败: ' + (response.message || '未知错误'), 'error');
            }
        } catch (error) {
            showAlert('加载版本历史失败: ' + error.message, 'error');
        } finally {
            hideLoading();
        }
    },

    // 渲染版本历史对话框
    renderVersionsDialog(fileId, fileName, versions) {
        const modalHtml = `
            <div class="modal-overlay" onclick="this.remove()">
                <div class="modal versions-modal" onclick="event.stopPropagation()">
                    <div class="modal-header">
                        <h3><i class="fas fa-history"></i> 版本历史 - ${fileName}</h3>
                        <button class="btn btn-icon" onclick="this.closest('.modal-overlay').remove()">
                            <i class="fas fa-times"></i>
                        </button>
                    </div>
                    <div class="modal-body">
                        ${versions.length === 0 ? `
                            <div class="empty-state">
                                <i class="fas fa-history"></i>
                                <p>暂无版本历史</p>
                            </div>
                        ` : `
                            <div class="versions-list">
                                ${versions.map(version => this.renderVersionItem(fileId, fileName, version)).join('')}
                            </div>
                        `}
                        
                        <div class="form-actions">
                            <button class="btn btn-secondary" onclick="this.closest('.modal-overlay').remove()">
                                关闭
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
        
        document.getElementById('modalContainer').innerHTML = modalHtml;
    },

    // 渲染版本项
    renderVersionItem(fileId, fileName, version) {
        const isCurrent = version.current || false;
        const uploadTime = formatDateTime(version.createdAt || version.uploadTime);
        const size = formatFileSize(version.fileSize || version.size || 0);

        return `
            <div class="version-item ${isCurrent ? 'current' : ''}">
                <div class="version-info">
                    <div class="version-header">
                        <h4 class="version-number">版本 ${version.versionNumber}</h4>
                        ${isCurrent ? '<span class="badge badge-success">当前版本</span>' : ''}
                    </div>
                    
                    <div class="version-details">
                        <p class="version-meta">
                            <span><i class="fas fa-calendar"></i> ${uploadTime}</span>
                            <span><i class="fas fa-file"></i> ${size}</span>
                        </p>
                    </div>
                </div>
                
                <div class="version-actions">
                    <button class="btn btn-sm btn-primary" onclick="fileManager.downloadVersion('${fileId}', '${fileName}', ${version.versionNumber})">
                        <i class="fas fa-download"></i> 下载
                    </button>
                    ${!isCurrent ? `
                        <button class="btn btn-sm btn-secondary" onclick="fileManager.restoreVersion('${fileId}', '${fileName}', ${version.versionNumber})">
                            <i class="fas fa-undo"></i> 恢复
                        </button>
                    ` : ''}
                </div>
            </div>
        `;
    },

    // 下载特定版本
    async downloadVersion(fileId, fileName, version) {
        try {
            showLoading('下载中...');
            const response = await api.downloadFileVersion(fileId, version);

            if (response.ok) {
                const blob = await response.blob();
                downloadFile(blob, `${fileName}_v${version}`);
                showAlert('下载成功！', 'success');
            } else {
                showAlert('下载失败', 'error');
            }
        } catch (error) {
            showAlert('下载失败: ' + error.message, 'error');
        } finally {
            hideLoading();
        }
    },

    // 恢复到特定版本
    async restoreVersion(fileId, fileName, version) {
        if (!confirm(`确定要恢复文件 "${fileName}" 到版本 ${version} 吗？这将创建新版本。`)) {
            return;
        }

        try {
            showLoading('恢复中...');
            const response = await api.restoreFileVersion(fileId, version);

            if (response.success) {
                showAlert('恢复成功！', 'success');
                this.loadFiles(); // 刷新文件列表
                // 关闭模态框
                document.querySelector('.modal-overlay').remove();
            } else {
                showAlert(response.message || '恢复失败', 'error');
            }
        } catch (error) {
            showAlert('恢复失败: ' + error.message, 'error');
        } finally {
            hideLoading();
        }
    },

    // 显示同步状态
    showSyncStatus() {
        // 显示同步状态对话框
        const modalHtml = `
            <div class="modal-overlay" onclick="this.remove()">
                <div class="modal sync-status-modal" onclick="event.stopPropagation()">
                    <div class="modal-header">
                        <h3><i class="fas fa-sync"></i> 同步状态</h3>
                        <button class="btn btn-icon" onclick="this.closest('.modal-overlay').remove()">
                            <i class="fas fa-times"></i>
                        </button>
                    </div>
                    <div class="modal-body">
                        <div class="sync-info">
                            <div class="sync-item">
                                <span class="sync-label">连接状态:</span>
                                <span class="sync-value" id="syncConnectionStatus">
                                    ${syncManager ? (syncManager.isConnected() ? '已连接' : '未连接') : '未启动'}
                                </span>
                            </div>
                            <div class="sync-item">
                                <span class="sync-label">重连次数:</span>
                                <span class="sync-value" id="syncReconnectCount">
                                    ${syncManager ? syncManager.reconnectAttempts : 0}
                                </span>
                            </div>
                        </div>
                        
                        <div class="sync-controls">
                            <button class="btn btn-primary" onclick="fileManager.testDeltaSync()">
                                <i class="fas fa-exchange-alt"></i> 测试差分同步
                            </button>
                            <button class="btn btn-secondary" onclick="syncManager.start()">
                                <i class="fas fa-play"></i> 启动同步
                            </button>
                            <button class="btn btn-secondary" onclick="syncManager.stop()">
                                <i class="fas fa-stop"></i> 停止同步
                            </button>
                        </div>
                        
                        <div class="form-actions">
                            <button class="btn btn-secondary" onclick="this.closest('.modal-overlay').remove()">
                                关闭
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
        
        document.getElementById('modalContainer').innerHTML = modalHtml;
    },

    // 测试差分同步
    async testDeltaSync() {
        // 选择一个文件进行差分同步测试
        const files = this.files.filter(f => !f.directory && f.fileId);
        if (files.length === 0) {
            showAlert('没有可用于测试的文件', 'warning');
            return;
        }

        const testFile = files[0];
        showAlert(`正在测试文件 "${testFile.name}" 的差分同步...`, 'info');

        try {
            // 获取文件签名
            const signaturesResponse = await api.getFileSignatures(testFile.fileId);
            if (signaturesResponse.success) {
                showAlert(`文件 "${testFile.name}" 支持差分同步，有 ${signaturesResponse.data.length} 个块`, 'success');
            } else {
                showAlert('该文件不支持差分同步（可能未分块存储）', 'info');
            }
        } catch (error) {
            showAlert('差分同步测试失败: ' + error.message, 'error');
        }
    },
};

