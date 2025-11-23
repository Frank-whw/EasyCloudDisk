// 文件共享管理模块

const shareManager = {
    // 显示文件共享对话框
    showShareDialog(fileId, fileName) {
        const modalHtml = `
            <div class="modal-overlay" onclick="this.remove()">
                <div class="modal share-modal" onclick="event.stopPropagation()">
                    <div class="modal-header">
                        <h3><i class="fas fa-share-alt"></i> 分享文件</h3>
                        <button class="btn btn-icon" onclick="this.closest('.modal-overlay').remove()">
                            <i class="fas fa-times"></i>
                        </button>
                    </div>
                    <div class="modal-body">
                        <div class="share-file-info">
                            <i class="fas fa-file"></i>
                            <span class="file-name">${fileName}</span>
                        </div>
                        
                        <form id="shareForm" onsubmit="event.preventDefault(); shareManager.createShare('${fileId}');">
                            <div class="form-group">
                                <label>分享名称</label>
                                <input type="text" id="shareName" placeholder="输入分享名称（可选）" value="${fileName}">
                            </div>
                            
                            <div class="form-group">
                                <label>分享描述</label>
                                <textarea id="shareDescription" placeholder="输入分享描述（可选）" rows="3"></textarea>
                            </div>
                            
                            <div class="form-group">
                                <label>访问权限</label>
                                <select id="sharePermission" required>
                                    <option value="READ_ONLY">只读</option>
                                    <option value="DOWNLOAD_ONLY">仅下载</option>
                                    <option value="READ_WRITE">读写</option>
                                </select>
                            </div>
                            
                            <div class="form-group">
                                <label>访问密码（可选）</label>
                                <input type="password" id="sharePassword" placeholder="设置访问密码">
                            </div>
                            
                            <div class="form-group">
                                <label>过期时间（可选）</label>
                                <input type="datetime-local" id="shareExpires">
                            </div>
                            
                            <div class="form-group">
                                <label>最大下载次数（可选）</label>
                                <input type="number" id="shareMaxDownloads" min="1" placeholder="不限制">
                            </div>
                            
                            <div class="form-actions">
                                <button type="button" class="btn btn-secondary" onclick="this.closest('.modal-overlay').remove()">
                                    取消
                                </button>
                                <button type="submit" class="btn btn-primary">
                                    <i class="fas fa-share"></i> 创建分享
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        `;
        
        document.getElementById('modalContainer').innerHTML = modalHtml;
    },

    // 创建文件分享
    async createShare(fileId) {
        try {
            const shareData = {
                shareName: document.getElementById('shareName').value.trim() || null,
                shareDescription: document.getElementById('shareDescription').value.trim() || null,
                permission: document.getElementById('sharePermission').value,
                password: document.getElementById('sharePassword').value.trim() || null,
                expiresAt: document.getElementById('shareExpires').value ? 
                          new Date(document.getElementById('shareExpires').value).toISOString() : null,
                maxDownloads: document.getElementById('shareMaxDownloads').value ? 
                             parseInt(document.getElementById('shareMaxDownloads').value) : null
            };

            showLoading('创建分享中...');
            
            // 检查API对象
            if (typeof api === 'undefined') {
                throw new Error('API 对象未初始化');
            }
            
            let response;
            if (typeof api.createShare === 'function') {
                response = await api.createShare(fileId, shareData);
            } else if (typeof api.post === 'function') {
                response = await api.post(`/files/${fileId}/share`, shareData);
            } else {
                throw new Error('API 方法不可用');
            }
            
            if (response && response.success) {
                hideLoading();
                document.querySelector('.modal-overlay').remove();
                this.showShareSuccessDialog(response.data);
                showAlert('分享创建成功！', 'success');
            } else {
                throw new Error(response?.message || '创建分享失败');
            }
        } catch (error) {
            hideLoading();
            console.error('创建分享失败:', error);
            showAlert('创建分享失败: ' + error.message, 'error');
        }
    },

    // 显示分享成功对话框
    showShareSuccessDialog(shareData) {
        const shareUrl = `${window.location.origin}/share.html?id=${shareData.shareId}`;
        
        const modalHtml = `
            <div class="modal-overlay" onclick="this.remove()">
                <div class="modal share-success-modal" onclick="event.stopPropagation()">
                    <div class="modal-header">
                        <h3><i class="fas fa-check-circle text-success"></i> 分享创建成功</h3>
                        <button class="btn btn-icon" onclick="this.closest('.modal-overlay').remove()">
                            <i class="fas fa-times"></i>
                        </button>
                    </div>
                    <div class="modal-body">
                        <div class="share-info">
                            <div class="share-url-group">
                                <label>分享链接</label>
                                <div class="input-group">
                                    <input type="text" id="shareUrl" value="${shareUrl}" readonly>
                                    <button class="btn btn-secondary" onclick="shareManager.copyToClipboard('shareUrl')">
                                        <i class="fas fa-copy"></i> 复制
                                    </button>
                                </div>
                            </div>
                            
                            ${shareData.hasPassword ? `
                                <div class="share-password-group">
                                    <label>访问密码</label>
                                    <div class="input-group">
                                        <input type="text" id="sharePasswordDisplay" value="已设置密码" readonly>
                                        <small class="text-muted">访问者需要输入密码才能访问</small>
                                    </div>
                                </div>
                            ` : ''}
                            
                            ${shareData.expiresAt ? `
                                <div class="share-expires-group">
                                    <label>过期时间</label>
                                    <p class="text-muted">${new Date(shareData.expiresAt).toLocaleString()}</p>
                                </div>
                            ` : ''}
                            
                            ${shareData.maxDownloads ? `
                                <div class="share-downloads-group">
                                    <label>下载限制</label>
                                    <p class="text-muted">最多 ${shareData.maxDownloads} 次下载</p>
                                </div>
                            ` : ''}
                        </div>
                        
                        <div class="form-actions">
                            <button type="button" class="btn btn-secondary" onclick="this.closest('.modal-overlay').remove()">
                                关闭
                            </button>
                            <button type="button" class="btn btn-primary" onclick="shareManager.copyToClipboard('shareUrl')">
                                <i class="fas fa-copy"></i> 复制链接
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
        
        document.getElementById('modalContainer').innerHTML = modalHtml;
    },

    // 显示我的分享列表
    async showMyShares() {
        try {
            showLoading('加载分享列表...');
            
            // 检查API对象是否存在
            if (typeof api === 'undefined') {
                throw new Error('API 对象未初始化');
            }
            
            let response;
            // 尝试使用不同的方法调用API
            if (typeof api.getUserShares === 'function') {
                response = await api.getUserShares();
            } else if (typeof api.get === 'function') {
                response = await api.get('/shares');
            } else {
                throw new Error('API 方法不可用，请检查 api.js 是否正确加载');
            }
            
            if (response && response.success) {
                hideLoading();
                this.renderSharesDialog(response.data || []);
            } else {
                throw new Error(response?.message || '加载分享列表失败');
            }
        } catch (error) {
            hideLoading();
            console.error('加载分享列表失败:', error);
            console.error('错误详情:', {
                message: error.message,
                stack: error.stack,
                apiExists: typeof api !== 'undefined',
                apiMethods: typeof api !== 'undefined' ? Object.getOwnPropertyNames(Object.getPrototypeOf(api)) : 'N/A'
            });
            showAlert('加载分享列表失败: ' + error.message, 'error');
            
            // 显示空的分享列表对话框
            this.renderSharesDialog([]);
        }
    },

    // 渲染分享列表对话框
    renderSharesDialog(shares) {
        const modalHtml = `
            <div class="modal-overlay" onclick="this.remove()">
                <div class="modal shares-list-modal" onclick="event.stopPropagation()">
                    <div class="modal-header">
                        <h3><i class="fas fa-share-alt"></i> 我的分享</h3>
                        <button class="btn btn-icon" onclick="this.closest('.modal-overlay').remove()">
                            <i class="fas fa-times"></i>
                        </button>
                    </div>
                    <div class="modal-body">
                        ${shares.length === 0 ? `
                            <div class="empty-state">
                                <i class="fas fa-share-alt"></i>
                                <p>还没有创建任何分享</p>
                            </div>
                        ` : `
                            <div class="shares-list">
                                ${shares.map(share => this.renderShareItem(share)).join('')}
                            </div>
                        `}
                        
                        <div class="form-actions">
                            <button type="button" class="btn btn-secondary" onclick="this.closest('.modal-overlay').remove()">
                                关闭
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
        
        document.getElementById('modalContainer').innerHTML = modalHtml;
    },

    // 渲染单个分享项
    renderShareItem(share) {
        const shareUrl = `${window.location.origin}/share.html?id=${share.shareId}`;
        const isExpired = share.expiresAt && new Date(share.expiresAt) < new Date();
        const isMaxDownloads = share.maxDownloads && share.downloadCount >= share.maxDownloads;
        
        return `
            <div class="share-item ${!share.active || isExpired || isMaxDownloads ? 'inactive' : ''}">
                <div class="share-info">
                    <div class="share-header">
                        <h4 class="share-name">
                            <i class="fas fa-file"></i>
                            ${share.shareName || share.fileName}
                        </h4>
                        <div class="share-status">
                            ${!share.active ? '<span class="badge badge-danger">已取消</span>' : ''}
                            ${isExpired ? '<span class="badge badge-warning">已过期</span>' : ''}
                            ${isMaxDownloads ? '<span class="badge badge-warning">已达上限</span>' : ''}
                            ${share.active && !isExpired && !isMaxDownloads ? '<span class="badge badge-success">活跃</span>' : ''}
                        </div>
                    </div>
                    
                    <div class="share-details">
                        <p class="share-description">${share.shareDescription || '无描述'}</p>
                        <div class="share-meta">
                            <span><i class="fas fa-eye"></i> ${this.getPermissionText(share.permission)}</span>
                            <span><i class="fas fa-download"></i> ${share.downloadCount}/${share.maxDownloads || '∞'}</span>
                            <span><i class="fas fa-clock"></i> ${new Date(share.createdAt).toLocaleDateString()}</span>
                            ${share.hasPassword ? '<span><i class="fas fa-lock"></i> 有密码</span>' : ''}
                        </div>
                    </div>
                    
                    <div class="share-url">
                        <div class="input-group">
                            <input type="text" value="${shareUrl}" readonly>
                            <button class="btn btn-sm btn-secondary" onclick="shareManager.copyShareUrl('${shareUrl}')">
                                <i class="fas fa-copy"></i>
                            </button>
                        </div>
                    </div>
                </div>
                
                <div class="share-actions">
                    ${share.active ? `
                        <button class="btn btn-sm btn-danger" onclick="shareManager.cancelShare('${share.shareId}')">
                            <i class="fas fa-times"></i> 取消分享
                        </button>
                    ` : ''}
                </div>
            </div>
        `;
    },

    // 获取权限文本
    getPermissionText(permission) {
        const permissions = {
            'READ_ONLY': '只读',
            'DOWNLOAD_ONLY': '仅下载',
            'READ_WRITE': '读写'
        };
        return permissions[permission] || permission;
    },

    // 取消分享
    async cancelShare(shareId) {
        if (!confirm('确定要取消这个分享吗？取消后分享链接将失效。')) {
            return;
        }

        try {
            showLoading('取消分享中...');
            const response = await api.cancelShare(shareId);
            
            if (response.success) {
                hideLoading();
                showAlert('分享已取消', 'success');
                // 重新加载分享列表
                this.showMyShares();
            } else {
                throw new Error(response.message || '取消分享失败');
            }
        } catch (error) {
            hideLoading();
            console.error('取消分享失败:', error);
            showAlert('取消分享失败: ' + error.message, 'error');
        }
    },

    // 复制分享链接
    copyShareUrl(url) {
        this.copyToClipboard(null, url);
    },

    // 复制到剪贴板
    copyToClipboard(elementId, text) {
        const textToCopy = text || document.getElementById(elementId).value;
        
        if (navigator.clipboard) {
            navigator.clipboard.writeText(textToCopy).then(() => {
                showAlert('链接已复制到剪贴板', 'success');
            }).catch(() => {
                this.fallbackCopyToClipboard(textToCopy);
            });
        } else {
            this.fallbackCopyToClipboard(textToCopy);
        }
    },

    // 备用复制方法
    fallbackCopyToClipboard(text) {
        const textArea = document.createElement('textarea');
        textArea.value = text;
        textArea.style.position = 'fixed';
        textArea.style.left = '-999999px';
        textArea.style.top = '-999999px';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        
        try {
            document.execCommand('copy');
            showAlert('链接已复制到剪贴板', 'success');
        } catch (err) {
            console.error('复制失败:', err);
            showAlert('复制失败，请手动复制', 'error');
        }
        
        document.body.removeChild(textArea);
    },

    // 检查文件分享状态
    async getFileShareStatus(fileId) {
        try {
            const response = await api.getFileShare(fileId);
            return response.success ? response.data : null;
        } catch (error) {
            console.error('获取文件分享状态失败:', error);
            return null;
        }
    }
};
