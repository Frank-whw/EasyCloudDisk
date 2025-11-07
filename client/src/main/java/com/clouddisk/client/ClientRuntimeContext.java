package com.clouddisk.client;

import com.clouddisk.client.config.ClientProperties;
import com.clouddisk.client.sync.DirectoryWatcher;
import com.clouddisk.client.sync.SyncManager;
import lombok.Data;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Data
public class ClientRuntimeContext {
    private String token;
    private String userId;
    private ClientProperties config; // 配置
    private CloseableHttpClient httpClient; // HTTP客户端
    private SyncManager syncManager; // 同步管理器
    private DirectoryWatcher directoryWatcher; // 文件监听器

    /**
     * 初始化运行时上下文
     */
    public void initialize() {
        // 初始化配置
        if(this.config == null) this.config = new ClientProperties();

        // 初始化HTTP客户端（带连接池配置）
        if(this.httpClient == null) this.httpClient = createHttpClient();

        // 创建同步管理器
        if(this.syncManager == null) this.syncManager = new SyncManager();

        // 创建文件监听器
        if(this.directoryWatcher == null) this.directoryWatcher = new DirectoryWatcher();
    }

    /**
     * 创建带连接池配置的HTTP客户端
     */
    private CloseableHttpClient createHttpClient() {
        return HttpClients.custom()
                .setMaxConnTotal(100) // 最大连接数
                .setMaxConnPerRoute(20) // 每个路由的最大连接数
                .setConnectionTimeToLive(30, TimeUnit.SECONDS) // 连接存活时间
                .build();
    }
    /**
     * 关闭并清理资源
     */
    public void shutdown() {
        // 关闭HTTP客户端
        if (this.httpClient != null) {
            try {
                this.httpClient.close();
            } catch (IOException e) {
                // 记录日志或处理异常
                e.printStackTrace();
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
    }
}