// API配置
const CONFIG = {
    // API基础URL - 根据实际情况修改
    
    // 本地开发环境（调试时使用）
    API_BASE_URL: 'http://localhost:8080',
    
    // 生产环境（EC2）
    // API_BASE_URL: 'http://54.95.61.230:8080',
    
    // 上传配置
    UPLOAD: {
        MAX_FILE_SIZE: 100 * 1024 * 1024, // 100MB
        CHUNK_SIZE: 2 * 1024 * 1024, // 2MB for resumable upload
        BLOCK_SIZE: 4 * 1024 * 1024, // 4MB for deduplication
    },
    
    // 存储键名
    STORAGE_KEYS: {
        AUTH_TOKEN: 'authToken',
        REFRESH_TOKEN: 'refreshToken',
        USER_EMAIL: 'userEmail',
        USER_ID: 'userId',
    },
    
    // 视图模式
    VIEW_MODES: {
        GRID: 'grid',
        LIST: 'list',
    },
    
    // 文件类型图标映射
    FILE_ICONS: {
        // 文档
        'pdf': 'fas fa-file-pdf',
        'doc': 'fas fa-file-word',
        'docx': 'fas fa-file-word',
        'xls': 'fas fa-file-excel',
        'xlsx': 'fas fa-file-excel',
        'ppt': 'fas fa-file-powerpoint',
        'pptx': 'fas fa-file-powerpoint',
        'txt': 'fas fa-file-alt',
        'md': 'fas fa-file-alt',
        
        // 图片
        'jpg': 'fas fa-file-image',
        'jpeg': 'fas fa-file-image',
        'png': 'fas fa-file-image',
        'gif': 'fas fa-file-image',
        'svg': 'fas fa-file-image',
        'webp': 'fas fa-file-image',
        
        // 视频
        'mp4': 'fas fa-file-video',
        'avi': 'fas fa-file-video',
        'mov': 'fas fa-file-video',
        'wmv': 'fas fa-file-video',
        
        // 音频
        'mp3': 'fas fa-file-audio',
        'wav': 'fas fa-file-audio',
        'flac': 'fas fa-file-audio',
        
        // 压缩文件
        'zip': 'fas fa-file-archive',
        'rar': 'fas fa-file-archive',
        '7z': 'fas fa-file-archive',
        'tar': 'fas fa-file-archive',
        'gz': 'fas fa-file-archive',
        
        // 代码
        'js': 'fas fa-file-code',
        'ts': 'fas fa-file-code',
        'html': 'fas fa-file-code',
        'css': 'fas fa-file-code',
        'java': 'fas fa-file-code',
        'py': 'fas fa-file-code',
        'cpp': 'fas fa-file-code',
        'c': 'fas fa-file-code',
        
        // 默认
        'default': 'fas fa-file',
        'folder': 'fas fa-folder',
    },
};

