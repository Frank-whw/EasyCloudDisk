package com.clouddisk.client.sync;

import com.clouddisk.client.http.FileApiClient;
import com.clouddisk.client.model.FileUploadRequest;
import com.clouddisk.client.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 同步管理器
 * 负责管理文件同步的核心逻辑，包括本地文件监听和远程文件同步
 */
@Slf4j
public class SyncManager {
    /**
     * 目录监听器，用于监听本地文件系统的变化
     */
    private DirectoryWatcher directoryWatcher;
    
    /**
     * 线程池，用于执行异步任务
     */
    private ExecutorService executorService;
    
    /**
     * 冲突解决器，用于处理本地和远程文件之间的冲突
     */
    private ConflictResolver conflictResolver;
    
    /**
     * 文件API客户端，用于与服务器进行文件操作
     */
    private FileApiClient fileApiClient;
    
    /**
     * 压缩服务，用于文件压缩和解压缩
     */
    private CompressionService compressionService;
    
    /**
     * 哈希计算器，用于计算文件哈希值
     */
    private HashCalculator hashCalculator;
    
    /**
     * 标识是否正在监听文件变化
     */
    private volatile boolean watching = false;

    /**
     * 构造函数
     * 初始化目录监听器、线程池和冲突解决器
     */
    public SyncManager() {
        this.directoryWatcher = new DirectoryWatcher();
        this.executorService = Executors.newSingleThreadExecutor();
        this.conflictResolver = new ConflictResolver();
        this.compressionService = new CompressionService();
        this.hashCalculator = new HashCalculator();
    }
    
    /**
     * 设置文件API客户端
     * @param fileApiClient 文件API客户端
     */
    public void setFileApiClient(FileApiClient fileApiClient) {
        this.fileApiClient = fileApiClient;
    }
    
    /**
     * 启动文件监听
     * 注册事件监听器并启动后台监听线程
     */
    public void startWatching() {
        if (!watching) {
            watching = true;
            log.info("启动文件监听服务");
            
            // 设置事件监听器
            directoryWatcher.setEventListener(this::handleLocalEvent);
            
            // 在后台线程中启动监听
            executorService.submit(() -> {
                try {
                    directoryWatcher.start();
                } catch (Exception e) {
                    log.error("文件监听启动失败", e);
                }
            });
            
            log.info("文件监听服务已启动");
        }
    }
    
    /**
     * 停止文件监听
     * 关闭监听器和线程池，释放相关资源
     */
    public void stopWatching() {
        if (watching) {
            log.info("停止文件监听服务");
            watching = false;
            
            // 停止目录监听
            if (directoryWatcher != null) {
                directoryWatcher.stop();
            }
            
            // 关闭线程池
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
            }
            
            log.info("文件监听服务已停止");
        }
    }
    
    /**
     * 处理本地文件事件
     * 当本地文件系统发生变化时，该方法会被调用
     * @param event 文件事件
     */
    public void handleLocalEvent(FileEvent event) {
        try {
            log.debug("处理本地文件事件: {} -> {}", event.getEventType(), event.getFilePath());
            
            // 根据事件类型执行相应的同步操作
            switch (event.getEventType()) {
                case CREATE:
                    handleFileCreate(event);
                    break;
                case MODIFY:
                    handleFileModify(event);
                    break;
                case DELETE:
                    handleFileDelete(event);
                    break;
                case UNKNOWN:
                    log.warn("未知的文件事件类型: {}", event.getFilePath());
                    break;
            }
            
        } catch (Exception e) {
            log.error("处理本地文件事件时发生错误", e);
        }
    }
    
    /**
     * 处理文件创建事件
     * @param event 文件事件
     */
    private void handleFileCreate(FileEvent event) {
        log.debug("处理文件创建事件: {}", event.getFilePath());
        // 上传新创建的文件到服务器
        uploadFile(event.getFilePath());
    }
    
    /**
     * 处理文件修改事件
     * @param event 文件事件
     */
    private void handleFileModify(FileEvent event) {
        log.debug("处理文件修改事件: {}", event.getFilePath());
        // 上传修改后的文件到服务器
        uploadFile(event.getFilePath());
    }
    
    /**
     * 处理文件删除事件
     * @param event 文件事件
     */
    private void handleFileDelete(FileEvent event) {
        log.debug("处理文件删除事件: {}", event.getFilePath());
        // TODO: 实现文件删除后的同步逻辑
        // 例如：从服务器删除对应的文件
    }
    
    /**
     * 上传文件到服务器
     * @param filePath 文件路径
     */
    private void uploadFile(Path filePath) {
        if (fileApiClient == null) {
            log.warn("文件API客户端未初始化，无法上传文件: {}", filePath);
            return;
        }
        
        try {
            // 检查文件是否存在
            FileUtils.checkFile(filePath);
            
            // 计算文件哈希值
            String contentHash = hashCalculator.computeFileHash(filePath);
            if (contentHash == null) {
                log.error("计算文件哈希值失败: {}", filePath);
                return;
            }
            
            // 压缩文件
            byte[] compressedPayload = compressionService.compress(filePath);
            if (compressedPayload == null || compressedPayload.length == 0) {
                log.error("文件压缩失败: {}", filePath);
                return;
            }
            
            // 创建文件上传请求
            FileUploadRequest request = new FileUploadRequest();
            request.setLocalPath(filePath);
            request.setFilePath(filePath.toString());
            request.setCompressedPayload(compressedPayload);
            request.setContentHash(contentHash);
            
            // 异步上传文件
            executorService.submit(() -> {
                try {
                    log.info("开始上传文件: {}", filePath);
                    boolean success = fileApiClient.uploadFile(request);
                    if (success) {
                        log.info("文件上传完成: {}", filePath);
                    } else {
                        log.error("文件上传失败: {}", filePath);
                    }
                } catch (Exception e) {
                    log.error("文件上传失败: {}", filePath, e);
                }
            });
            
        } catch (Exception e) {
            log.error("准备文件上传时发生错误: {}", filePath, e);
        }
    }
    
    /**
     * 同步远程变更
     * 从服务器获取变更并应用到本地
     */
    public void synchronizeRemoteChanges() {
        try {
            log.debug("开始同步远程变更");
            
            // TODO: 实现从服务器获取变更并应用到本地的逻辑
            // 1. 获取服务器上的文件列表
            // 2. 比较本地和远程文件的差异
            // 3. 下载新的或修改的文件
            // 4. 删除本地已从服务器删除的文件
            
            log.debug("远程变更同步完成");
        } catch (Exception e) {
            log.error("同步远程变更时发生错误", e);
        }
    }
    
    /**
     * 设置冲突解决器
     * @param conflictResolver 冲突解决器
     */
    public void setConflictResolver(ConflictResolver conflictResolver) {
        this.conflictResolver = conflictResolver;
    }
    
    /**
     * 获取冲突解决器
     * @return 冲突解决器
     */
    public ConflictResolver getConflictResolver() {
        return conflictResolver;
    }
}