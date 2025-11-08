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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Data
@Slf4j
public class ClientRuntimeContext {
    private String token;
    private String userId;
    private ClientProperties config; // 配置
    private CloseableHttpClient httpClient; // HTTP客户端
    private SyncManager syncManager; // 同步管理器
    private DirectoryWatcher directoryWatcher; // 文件监听器
    private FileApiClient fileApiClient; // 文件API客户端

    /**
     * 初始化运行时上下文
     */
    public void initialize() {
        log.info("初始化运行时上下文...");
        
        // 初始化配置
        if(this.config == null) this.config = new ClientProperties();

        // 初始化HTTP客户端（带连接池配置）
        if(this.httpClient == null) this.httpClient = createHttpClient();

        // 创建文件API客户端
        if(this.fileApiClient == null) this.fileApiClient = new FileApiClient(this.httpClient);

        // 创建同步管理器
        if(this.syncManager == null) this.syncManager = new SyncManager();
        
        // 设置文件API客户端
        this.syncManager.setFileApiClient(this.fileApiClient);

        // 创建文件监听器
        if(this.directoryWatcher == null) this.directoryWatcher = new DirectoryWatcher();
        
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
        } catch (IOException e) {
            log.error("配置文件监听器失败", e);
            throw new RuntimeException("配置文件监听器失败", e);
        }
        
        log.info("运行时上下文初始化完成");
    }

    /**
     * 创建带连接池配置的HTTP客户端
     */
    private CloseableHttpClient createHttpClient() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(20);
        connectionManager.setDefaultConnectionConfig(
                ConnectionConfig.custom()
                        .setTimeToLive(TimeValue.of(30, TimeUnit.SECONDS))
                        .build());
        
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();
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