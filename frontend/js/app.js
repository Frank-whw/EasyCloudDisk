// 应用入口文件

// 应用初始化
document.addEventListener('DOMContentLoaded', () => {
    // 确保全局对象已初始化后再导出
    if (typeof auth !== 'undefined') {
        window.auth = auth;
    }
    if (typeof fileManager !== 'undefined') {
        window.fileManager = fileManager;
    }
    if (typeof uploadManager !== 'undefined') {
        window.uploadManager = uploadManager;
    }
    if (typeof syncManager !== 'undefined') {
        window.syncManager = syncManager;
    }
    if (typeof closeModal !== 'undefined') {
        window.closeModal = closeModal;
    }
    
    // 初始化应用
    init();
});

async function init() {
    // 检查认证状态
    if (auth && auth.isAuthenticated()) {
        // 如果有token，直接显示主页面
        // token的有效性会在后续API调用中验证，如果无效会返回401并自动跳转到登录页
        auth.showMainPage();
    } else if (auth) {
        auth.showAuthPage();
    }

    // 初始化文件管理器
    if (fileManager && typeof fileManager.init === 'function') {
        fileManager.init();
    }

    // 恢复视图模式
    const savedViewMode = localStorage.getItem('viewMode');
    if (savedViewMode && fileManager) {
        fileManager.viewMode = savedViewMode;
    }

    // 隐藏加载屏幕
    if (typeof hideLoading === 'function') {
        hideLoading();
    }
}

// 全局错误处理
window.addEventListener('error', (event) => {
    console.error('全局错误:', event.error);
    if (typeof showAlert === 'function') {
        showAlert('发生错误: ' + (event.error?.message || '未知错误'), 'error');
    }
});

// 处理未捕获的Promise拒绝
window.addEventListener('unhandledrejection', (event) => {
    console.error('未处理的Promise拒绝:', event.reason);
    if (typeof showAlert === 'function') {
        showAlert('操作失败: ' + (event.reason?.message || '未知错误'), 'error');
    }
});

// 页面可见性变化时重新连接同步
document.addEventListener('visibilitychange', () => {
    if (!document.hidden && auth && auth.isAuthenticated()) {
        // 页面重新可见时，检查同步连接
        if (syncManager && typeof syncManager.isConnected === 'function' && !syncManager.isConnected()) {
            if (typeof syncManager.start === 'function') {
                syncManager.start();
            }
        }
    }
});

