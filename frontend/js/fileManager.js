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
        const size = formatFileSize(file.fileSize || 0);
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
                    </button>` : ''}
                    <button class="btn btn-danger btn-small" onclick="fileManager.deleteFile('${file.fileId}')">
                        <i class="fas fa-trash"></i> 删除
                    </button>
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
                    </button>` : ''}
                    <button class="btn btn-danger btn-small" onclick="fileManager.deleteFile('${file.fileId}')">
                        <i class="fas fa-trash"></i> 删除
                    </button>
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
                
                // 路径处理：确保路径正确拼接
                const currentPath = file.path || this.currentPath;
                const separator = currentPath.endsWith('/') ? '' : '/';
                const targetPath = currentPath + separator + file.name;
                
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
            showLoading();
            const response = await api.downloadFile(fileId);

            if (response.ok) {
                const blob = await response.blob();
                downloadFile(blob, fileName);
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

    // 删除文件
    async deleteFile(fileId) {
        if (!confirm('确定要删除这个文件吗？此操作不可恢复。')) {
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
        
        // 更新按钮状态
        document.querySelectorAll('.view-toggle .btn-icon').forEach(btn => {
            btn.classList.remove('active');
        });
        event.target.closest('.btn-icon').classList.add('active');
        
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

    // 显示同步状态
    showSyncStatus() {
        // TODO: 实现同步状态显示
        showAlert('同步状态功能开发中', 'info');
    }
};

