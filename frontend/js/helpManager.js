// 帮助和指南管理模块

const helpManager = {
    // 显示分享功能帮助
    showShareHelp() {
        const modalHtml = `
            <div class="modal-overlay" onclick="this.remove()">
                <div class="modal help-modal" onclick="event.stopPropagation()">
                    <div class="modal-header">
                        <h3><i class="fas fa-question-circle"></i> 如何使用分享功能</h3>
                        <button class="btn btn-icon" onclick="this.closest('.modal-overlay').remove()">
                            <i class="fas fa-times"></i>
                        </button>
                    </div>
                    <div class="modal-body">
                        <div class="help-content">
                            <div class="help-step">
                                <div class="step-number">1</div>
                                <div class="step-content">
                                    <h4>找到分享按钮</h4>
                                    <p>在文件列表中，每个文件都有一个灰色的 <strong>🔗 分享</strong> 按钮。点击它来创建分享链接。</p>
                                    <div class="button-example">
                                        <span class="btn-demo primary">📥 下载</span>
                                        <span class="btn-demo secondary highlight">🔗 分享</span>
                                        <span class="btn-demo danger">🗑️ 删除</span>
                                    </div>
                                </div>
                            </div>
                            
                            <div class="help-step">
                                <div class="step-number">2</div>
                                <div class="step-content">
                                    <h4>配置分享设置</h4>
                                    <p>在弹出的对话框中设置：</p>
                                    <ul>
                                        <li><strong>访问权限</strong>：选择"仅下载"让别人下载文件</li>
                                        <li><strong>访问密码</strong>：可选，为分享设置密码保护</li>
                                        <li><strong>过期时间</strong>：可选，设置分享的有效期</li>
                                    </ul>
                                </div>
                            </div>
                            
                            <div class="help-step">
                                <div class="step-number">3</div>
                                <div class="step-content">
                                    <h4>分享链接</h4>
                                    <p>创建成功后，复制生成的链接发送给需要访问文件的人。如果设置了密码，记得告诉对方密码。</p>
                                </div>
                            </div>
                            
                            <div class="help-step">
                                <div class="step-number">4</div>
                                <div class="step-content">
                                    <h4>管理分享</h4>
                                    <p>点击侧边栏的 <strong>📤 我的分享</strong> 可以查看和管理所有创建的分享。</p>
                                </div>
                            </div>
                        </div>
                        
                        <div class="help-tips">
                            <h4><i class="fas fa-lightbulb"></i> 小贴士</h4>
                            <ul>
                                <li>建议为重要文件设置密码保护</li>
                                <li>可以随时取消不需要的分享</li>
                                <li>分享链接包含随机ID，相对安全</li>
                            </ul>
                        </div>
                        
                        <div class="help-actions">
                            <button class="btn btn-secondary" onclick="this.closest('.modal-overlay').remove()">
                                知道了
                            </button>
                            <button class="btn btn-primary" onclick="window.open('分享功能快速指南.html', '_blank')">
                                查看详细指南
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
        
        document.getElementById('modalContainer').innerHTML = modalHtml;
    },

    // 显示首次使用提示
    showFirstTimeHelp() {
        // 检查是否是首次使用
        if (localStorage.getItem('share_help_shown') === 'true') {
            return;
        }

        setTimeout(() => {
            const modalHtml = `
                <div class="modal-overlay" onclick="this.remove()">
                    <div class="modal welcome-modal" onclick="event.stopPropagation()">
                        <div class="modal-header">
                            <h3><i class="fas fa-star"></i> 欢迎使用文件分享功能！</h3>
                            <button class="btn btn-icon" onclick="this.closest('.modal-overlay').remove()">
                                <i class="fas fa-times"></i>
                            </button>
                        </div>
                        <div class="modal-body">
                            <div class="welcome-content">
                                <div class="welcome-icon">
                                    <i class="fas fa-share-alt"></i>
                                </div>
                                <h4>现在您可以轻松分享文件了！</h4>
                                <p>只需要点击文件旁边的 <strong>🔗 分享</strong> 按钮，就可以创建分享链接发送给其他人。</p>
                                
                                <div class="feature-highlights">
                                    <div class="feature">
                                        <i class="fas fa-lock"></i>
                                        <span>密码保护</span>
                                    </div>
                                    <div class="feature">
                                        <i class="fas fa-clock"></i>
                                        <span>过期控制</span>
                                    </div>
                                    <div class="feature">
                                        <i class="fas fa-download"></i>
                                        <span>下载限制</span>
                                    </div>
                                </div>
                            </div>
                            
                            <div class="welcome-actions">
                                <button class="btn btn-secondary" onclick="helpManager.dismissFirstTimeHelp()">
                                    不再显示
                                </button>
                                <button class="btn btn-primary" onclick="helpManager.showShareHelp(); this.closest('.modal-overlay').remove();">
                                    了解更多
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            
            document.getElementById('modalContainer').innerHTML = modalHtml;
        }, 2000); // 2秒后显示
    },

    // 关闭首次使用提示
    dismissFirstTimeHelp() {
        localStorage.setItem('share_help_shown', 'true');
        document.querySelector('.modal-overlay').remove();
    }
};
