// API请求封装

class ApiClient {
    constructor(baseURL) {
        this.baseURL = baseURL;
    }

    // 获取认证头
    getAuthHeaders() {
        const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
        return token ? { 'Authorization': `Bearer ${token}` } : {};
    }

    // 通用请求方法
    async request(endpoint, options = {}) {
        const url = `${this.baseURL}${endpoint}`;
        const headers = {
            ...this.getAuthHeaders(),
            ...options.headers
        };

        // 如果不是FormData，默认使用JSON
        if (!(options.body instanceof FormData) && !headers['Content-Type']) {
            headers['Content-Type'] = 'application/json';
        }

        try {
            const response = await fetch(url, {
                ...options,
                headers
            });

            // 处理401未授权
            if (response.status === 401) {
                localStorage.removeItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
                localStorage.removeItem(CONFIG.STORAGE_KEYS.USER_EMAIL);
                localStorage.removeItem(CONFIG.STORAGE_KEYS.USER_ID);
                window.location.reload();
                throw new Error('未授权，请重新登录');
            }

            // 处理二进制响应（如下载）
            if (options.responseType === 'blob' || response.headers.get('content-type')?.includes('application/octet-stream')) {
                return response;
            }

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.message || `请求失败: ${response.status}`);
            }

            return data;
        } catch (error) {
            console.error('API请求错误:', error);
            throw error;
        }
    }

    // GET请求
    async get(endpoint, options = {}) {
        return this.request(endpoint, { ...options, method: 'GET' });
    }

    // POST请求
    async post(endpoint, body, options = {}) {
        return this.request(endpoint, {
            ...options,
            method: 'POST',
            body: body instanceof FormData ? body : JSON.stringify(body)
        });
    }

    // PUT请求
    async put(endpoint, body, options = {}) {
        return this.request(endpoint, {
            ...options,
            method: 'PUT',
            body: JSON.stringify(body)
        });
    }

    // DELETE请求
    async delete(endpoint, options = {}) {
        return this.request(endpoint, { ...options, method: 'DELETE' });
    }

    // ==================== 认证相关 ====================
    
    // 注册
    async register(email, password) {
        return this.post('/auth/register', { email, password });
    }

    // 登录
    async login(email, password) {
        return this.post('/auth/login', { email, password });
    }

    // 刷新Token
    async refreshToken() {
        const refreshToken = localStorage.getItem(CONFIG.STORAGE_KEYS.REFRESH_TOKEN);
        if (!refreshToken) throw new Error('没有刷新令牌');
        return this.post('/auth/refresh', { refreshToken });
    }

    // ==================== 文件相关 ====================
    
    // 获取文件列表
    async listFiles(path = null) {
        const endpoint = path ? `/files?path=${encodeURIComponent(path)}` : '/files';
        return this.get(endpoint);
    }

    // 上传文件
    async uploadFile(file, path = null) {
        const formData = new FormData();
        formData.append('file', file);
        if (path) {
            formData.append('path', path);
        }
        return this.post('/files/upload', formData);
    }

    // 下载文件
    async downloadFile(fileId) {
        return this.request(`/files/${fileId}/download`, {
            method: 'GET',
            responseType: 'blob'
        });
    }

    // 删除文件
    async deleteFile(fileId) {
        return this.delete(`/files/${fileId}`);
    }

    // 创建目录
    async createDirectory(path, name) {
        return this.post('/files/directories', { path, name });
    }

    // ==================== 秒传相关 ====================
    
    // 检查是否可以秒传
    async checkQuickUpload(hash) {
        return this.post('/files/quick-check', { hash });
    }

    // 执行秒传
    async quickUpload(hash, fileName, path = null) {
        return this.post('/files/quick-upload', {
            hash,
            fileName,
            path: path || '/'
        });
    }

    // ==================== 断点续传相关 ====================
    
    // 初始化断点续传会话
    async initResumableUpload(fileName, path, fileSize) {
        return this.post('/files/resumable/init', {
            fileName,
            path: path || '/',
            fileSize
        });
    }

    // 上传分块
    async uploadChunk(sessionId, chunkIndex, chunk) {
        const formData = new FormData();
        formData.append('chunk', chunk);
        return this.post(`/files/resumable/${sessionId}/chunk/${chunkIndex}`, formData);
    }

    // 完成断点续传
    async completeResumableUpload(sessionId) {
        return this.post(`/files/resumable/${sessionId}/complete`);
    }

    // 获取上传会话列表
    async listUploadSessions() {
        return this.get('/files/resumable/sessions');
    }

    // ==================== 差分同步相关 ====================
    
    // 获取文件签名
    async getFileSignatures(fileId) {
        return this.get(`/files/${fileId}/signatures`);
    }

    // 应用差分更新
    async applyDelta(fileId, deltaChunks) {
        return this.post(`/files/${fileId}/delta`, { deltaChunks });
    }

    // ==================== 加密上传相关 ====================
    
    // 上传加密文件
    async uploadEncryptedFile(file, metadata) {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('metadata', JSON.stringify(metadata));
        return this.post('/files/upload-encrypted', formData);
    }

    // 获取加密元数据
    async getEncryptionMetadata(fileId) {
        return this.get(`/files/${fileId}/encryption`);
    }

    // 检查收敛加密秒传
    async checkConvergentQuickUpload(originalHash) {
        return this.post('/files/convergent-check', { originalHash });
    }

    // ==================== 健康检查 ====================
    
    // 健康检查
    async healthCheck() {
        return this.get('/health');
    }
}

// 创建全局API客户端实例
const api = new ApiClient(CONFIG.API_BASE_URL);

