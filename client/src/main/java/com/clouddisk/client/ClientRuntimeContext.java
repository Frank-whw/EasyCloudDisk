package com.clouddisk.client;

import com.clouddisk.client.config.ClientProperties;
import com.clouddisk.client.http.FileApiClient;
import com.clouddisk.client.sync.DirectoryWatcher;
import com.clouddisk.client.sync.SyncManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Data
@Slf4j
@Component
public class ClientRuntimeContext {
    private String token;
    private String userId;
    
    public void setToken(String token) {
        this.token = token;
        // 同时更新文件API客户端的认证令牌
        if (this.fileApiClient != null) {
            this.fileApiClient.setAuthToken(token);
        }
    }
    
    @Autowired
    private ClientProperties config; // 配置
    
    @Autowired
    private CloseableHttpClient httpClient; // HTTP客户端
    
    @Autowired
    private SyncManager syncManager; // 同步管理器
    
    @Autowired
    private DirectoryWatcher directoryWatcher; // 文件监听器
    
    private FileApiClient fileApiClient; // 文件API客户端

    /**
     * 初始化运行时上下文
     */
    public void initialize() {
        log.info("初始化运行时上下文...");
        
        // 验证配置
        config.validate();

        // 创建文件API客户端（使用配置的服务器地址）
        if(this.fileApiClient == null) this.fileApiClient = new FileApiClient(config.getServerUrl(), this.httpClient);
        
        // 设置认证令牌
        if (this.token != null) {
            this.fileApiClient.setAuthToken(this.token);
        }

        // 设置文件API客户端
        this.syncManager.setFileApiClient(this.fileApiClient);

        // 配置文件监听器
        try {
            Path syncDir = Paths.get(config.getSyncDir());
            File syncDirFile = syncDir.toFile();
            
            // 确保同步目录存在
            if (!syncDirFile.exists()) {
                if (!syncDirFile.mkdirs()) {
                    log.error("无法创建同步目录: {}", syncDirFile.getAbsolutePath());
                    throw new RuntimeException("同步目录创建失败");
                }
            }
            
            // 设置监听目录
            directoryWatcher.setWatchDir(syncDir);
            log.info("已设置监听目录: {}", syncDir);
        } catch (IOException e) {
            log.error("配置文件监听器失败", e);
            throw new RuntimeException("配置文件监听器失败", e);
        }
        
        log.info("运行时上下文初始化完成");
    }
    
    /**
     * 关闭并清理资源
     */
    public void shutdown() {
        log.info("关闭运行时上下文资源...");
        
        // 关闭HTTP客户端
        if (this.httpClient != null) {
            try {
                this.httpClient.close();
            } catch (IOException e) {
                log.error("关闭HTTP客户端时发生错误", e);
            }
        }

        // 停止目录监听
        if (this.directoryWatcher != null) {
            this.directoryWatcher.stop();
        }

        // 停止同步管理器
        if (this.syncManager != null) {
            this.syncManager.stopWatching();
        }

        // 清理认证信息
        this.token = null;
        this.userId = null;
        
        log.info("运行时上下文资源已关闭");
    }
}