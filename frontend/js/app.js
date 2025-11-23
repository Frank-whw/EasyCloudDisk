// 应用入口文件

// 应用初始化
document.addEventListener('DOMContentLoaded', () => {
    // 初始化应用
    init();
});

async function init() {
    // 检查认证状态
    if (auth.isAuthenticated()) {
        // 如果有token，直接显示主页面
        // token的有效性会在后续API调用中验证，如果无效会返回401并自动跳转到登录页
        auth.showMainPage();
    } else {
        auth.showAuthPage();
    }

    // 初始化文件管理器
    if (fileManager) {
        fileManager.init();
    }

    // 恢复视图模式
    const savedViewMode = localStorage.getItem('viewMode');
    if (savedViewMode) {
        fileManager.viewMode = savedViewMode;
    }

    // 隐藏加载屏幕
    hideLoading();
    
    // 如果用户已登录，显示首次使用帮助
    if (auth.isAuthenticated() && helpManager) {
        helpManager.showFirstTimeHelp();
    }
}

// 全局错误处理
window.addEventListener('error', (event) => {
    console.error('全局错误:', event.error);
    showAlert('发生错误: ' + (event.error?.message || '未知错误'), 'error');
});

// 处理未捕获的Promise拒绝
window.addEventListener('unhandledrejection', (event) => {
    console.error('未处理的Promise拒绝:', event.reason);
    showAlert('操作失败: ' + (event.reason?.message || '未知错误'), 'error');
});

// 页面可见性变化时重新连接同步
document.addEventListener('visibilitychange', () => {
    if (!document.hidden && auth.isAuthenticated()) {
        // 页面重新可见时，检查同步连接
        if (syncManager && !syncManager.isConnected()) {
            syncManager.start();
        }
    }
});

// 导出到全局（用于HTML中的onclick）
window.auth = auth;
window.fileManager = fileManager;
window.uploadManager = uploadManager;
window.syncManager = syncManager;
window.closeModal = closeModal;

