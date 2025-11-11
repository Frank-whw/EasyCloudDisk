package com.clouddisk.client.sync;

import com.clouddisk.client.http.FileApiClient;
import com.clouddisk.client.model.FileUploadRequest;
import com.clouddisk.client.service.S3Service;
import com.clouddisk.client.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
@Component
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
     * S3服务，用于直接与AWS S3交互
     */
    private S3Service s3Service;
    
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
     * 设置S3服务
     * @param s3Service S3服务
     */
    public void setS3Service(S3Service s3Service) {
        this.s3Service = s3Service;
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
     */
    public void stopWatching() {
        if (watching) {
            watching = false;
            log.info("停止文件监听服务");
            
            // 停止目录监听器
            if (directoryWatcher != null) {
                directoryWatcher.stop();
            }
            
            // 关闭线程池
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    /**
     * 处理本地文件事件
     * @param event 文件事件
     */
    public void handleLocalEvent(FileEvent event) {
        if (!watching) {
            log.warn("文件监听服务未启动，忽略事件: {}", event);
            return;
        }
        
        try {
            Path filePath = event.getFilePath();
            FileEvent.EventType eventType = event.getEventType();
            
            log.info("处理本地文件事件: {} - {}", eventType, filePath);
            
            switch (eventType) {
                case CREATE:
                case MODIFY:
                    handleFileUpload(filePath);
                    break;
                case DELETE:
                    // TODO: 处理文件删除事件
                    log.info("检测到文件删除事件: {}", filePath);
                    break;
                default:
                    log.warn("未知文件事件类型: {}", eventType);
            }
        } catch (Exception e) {
            log.error("处理本地文件事件时发生错误: {}", event, e);
        }
    }
    
    /**
     * 处理文件上传
     * @param filePath 文件路径
     */
    private void handleFileUpload(Path filePath) {
        try {
            log.debug("准备上传文件: {}", filePath);
            
            // 检查文件是否存在
            if (!Files.exists(filePath)) {
                log.warn("文件不存在，跳过上传: {}", filePath);
                return;
            }
            
            // 检查是否为目录
            if (Files.isDirectory(filePath)) {
                log.debug("跳过目录: {}", filePath);
                return;
            }
            
            // 计算文件哈希值
            String contentHash = hashCalculator.computeFileHash(filePath);
            if (contentHash == null) {
                log.error("计算文件哈希值失败: {}", filePath);
                return;
            }
            
            // 压缩文件
            byte[] compressedPayload = compressionService.compress(filePath);
            if (compressedPayload == null) {
                log.error("文件压缩失败: {}", filePath);
                return;
            }
            
            // 构建上传请求
            FileUploadRequest request = new FileUploadRequest();
            request.setLocalPath(filePath);
            request.setFilePath(filePath.toString());
            request.setCompressedPayload(compressedPayload);
            request.setContentHash(contentHash);
            
            // 异步上传文件 - 先验证去重，再决定上传方式
            executorService.submit(() -> {
                try {
                    log.info("开始上传文件: {} (哈希: {})", filePath, contentHash);
                    
                    // 步骤1: 先调用服务端API检查是否已存在（去重验证）
                    boolean shouldUpload = fileApiClient.checkFileExists(contentHash);
                    if (!shouldUpload) {
                        log.info("文件已存在，跳过上传: {} (哈希: {})", filePath, contentHash);
                        return;
                    }
                    
                    // 步骤2: 如果启用S3直接上传，先尝试S3
                    if (s3Service != null) {
                        log.info("尝试S3直接上传: {}", filePath);
                        boolean s3Success = s3Service.uploadFile(request);
                        if (s3Success) {
                            log.info("S3直接上传成功: {}", filePath);
                            // 通知服务端上传完成
                            fileApiClient.notifyUploadComplete(contentHash, filePath.toString());
                            return;
                        }
                    }
                    
                    // 步骤3: S3上传失败或禁用时，使用API上传
                    log.info("使用API上传文件: {}", filePath);
                    boolean apiSuccess = fileApiClient.uploadFile(request);
                    if (apiSuccess) {
                        log.info("API上传成功: {}", filePath);
                    } else {
                        log.error("API上传失败: {}", filePath);
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
            
            log.debug("远程变更同步完成");
        } catch (Exception e) {
            log.error("同步远程变更时发生错误", e);
        }
    }
}