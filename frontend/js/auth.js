// 认证管理模块

const auth = {
    // 显示登录表单
    showLogin() {
        document.getElementById('loginForm').classList.add('active');
        document.getElementById('registerForm').classList.remove('active');
        clearAlerts('authAlert');
    },

    // 显示注册表单
    showRegister() {
        document.getElementById('loginForm').classList.remove('active');
        document.getElementById('registerForm').classList.add('active');
        clearAlerts('authAlert');
    },

    // 登录
    async login() {
        const email = document.getElementById('loginEmail').value.trim();
        const password = document.getElementById('loginPassword').value;

        if (!email || !password) {
            showAlert('请填写所有字段', 'error', 'authAlert');
            return;
        }

        if (!isValidEmail(email)) {
            showAlert('请输入有效的邮箱地址', 'error', 'authAlert');
            return;
        }

        try {
            showLoading();
            const response = await api.login(email, password);

            if (response.success && response.data) {
                // 保存认证信息
                localStorage.setItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN, response.data.token);
                localStorage.setItem(CONFIG.STORAGE_KEYS.USER_EMAIL, email);
                localStorage.setItem(CONFIG.STORAGE_KEYS.USER_ID, response.data.userId);
                
                if (response.data.refreshToken) {
                    localStorage.setItem(CONFIG.STORAGE_KEYS.REFRESH_TOKEN, response.data.refreshToken);
                }

                showAlert('登录成功！', 'success', 'authAlert');
                
                setTimeout(() => {
                    this.showMainPage();
                }, 500);
            } else {
                showAlert(response.message || '登录失败', 'error', 'authAlert');
            }
        } catch (error) {
            showAlert(error.message || '登录失败，请检查网络连接', 'error', 'authAlert');
        } finally {
            hideLoading();
        }
    },

    // 注册
    async register() {
        const email = document.getElementById('registerEmail').value.trim();
        const password = document.getElementById('registerPassword').value;
        const passwordConfirm = document.getElementById('registerPasswordConfirm').value;

        if (!email || !password || !passwordConfirm) {
            showAlert('请填写所有字段', 'error', 'authAlert');
            return;
        }

        if (!isValidEmail(email)) {
            showAlert('请输入有效的邮箱地址', 'error', 'authAlert');
            return;
        }

        if (!isValidPassword(password)) {
            showAlert('密码至少8位，且包含字母和数字', 'error', 'authAlert');
            return;
        }

        if (password !== passwordConfirm) {
            showAlert('两次输入的密码不一致', 'error', 'authAlert');
            return;
        }

        try {
            showLoading();
            const response = await api.register(email, password);

            if (response.success && response.data) {
                // 保存认证信息
                localStorage.setItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN, response.data.token);
                localStorage.setItem(CONFIG.STORAGE_KEYS.USER_EMAIL, email);
                localStorage.setItem(CONFIG.STORAGE_KEYS.USER_ID, response.data.userId);
                
                if (response.data.refreshToken) {
                    localStorage.setItem(CONFIG.STORAGE_KEYS.REFRESH_TOKEN, response.data.refreshToken);
                }

                showAlert('注册成功！', 'success', 'authAlert');
                
                setTimeout(() => {
                    this.showMainPage();
                }, 500);
            } else {
                showAlert(response.message || '注册失败', 'error', 'authAlert');
            }
        } catch (error) {
            showAlert(error.message || '注册失败，请检查网络连接', 'error', 'authAlert');
        } finally {
            hideLoading();
        }
    },

    // 退出登录
    logout() {
        if (confirm('确定要退出登录吗？')) {
            localStorage.removeItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
            localStorage.removeItem(CONFIG.STORAGE_KEYS.REFRESH_TOKEN);
            localStorage.removeItem(CONFIG.STORAGE_KEYS.USER_EMAIL);
            localStorage.removeItem(CONFIG.STORAGE_KEYS.USER_ID);
            
            // 停止同步
            if (syncManager) {
                syncManager.stop();
            }
            
            this.showAuthPage();
        }
    },

    // 显示认证页面
    showAuthPage() {
        document.getElementById('authPage').classList.add('active');
        document.getElementById('mainPage').classList.remove('active');
        clearAlerts('authAlert');
    },

    // 显示主页面
    showMainPage() {
        document.getElementById('authPage').classList.remove('active');
        document.getElementById('mainPage').classList.add('active');
        
        const userEmail = localStorage.getItem(CONFIG.STORAGE_KEYS.USER_EMAIL);
        document.getElementById('userEmail').textContent = userEmail || '用户';
        
        // 加载文件列表
        if (fileManager) {
            fileManager.loadFiles();
        }
        
        // 启动同步
        if (syncManager) {
            syncManager.start();
        }
    },

    // 检查是否已登录
    isAuthenticated() {
        return !!localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
    }
};

