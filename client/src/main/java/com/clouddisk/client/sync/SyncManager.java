package com.clouddisk.client.sync;

import com.clouddisk.client.http.FileApiClient;
import com.clouddisk.client.model.FileMetadata;
import com.clouddisk.client.model.FileUploadRequest;
import com.clouddisk.client.util.FileUtils;
import com.clouddisk.client.util.RetryTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private final DirectoryWatcher directoryWatcher;

    /**
     * 线程池，用于执行异步任务
     */
    private final ExecutorService executorService;

    /**
     * 冲突解决器，用于处理本地和远程文件之间的冲突
     */
    private final ConflictResolver conflictResolver;

    /**
     * 压缩服务，用于文件压缩和解压缩
     */
    private final CompressionService compressionService;

    /**
     * 哈希计算器，用于计算文件哈希值
     */
    private final HashCalculator hashCalculator;

    /**
     * 重试模板，用于处理网络失败重试。
     */
    private final RetryTemplate retryTemplate;

    /**
     * 文件API客户端，用于与服务器进行文件操作
     */
    private FileApiClient fileApiClient;

    /**
     * 标识是否正在监听文件变化
     */
    private volatile boolean watching = false;

    public SyncManager() {
        this.directoryWatcher = new DirectoryWatcher();
        this.executorService = Executors.newFixedThreadPool(
            Math.max(Runtime.getRuntime().availableProcessors() / 2, 1),
            r -> {
                Thread thread = new Thread(r, "sync-manager-worker");
                thread.setDaemon(true);
                return thread;
            });
        this.conflictResolver = new ConflictResolver();
        this.compressionService = new CompressionService();
        this.hashCalculator = new HashCalculator();
        this.retryTemplate = new RetryTemplate();
    }

    /**
     * 设置文件API客户端
     * @param fileApiClient 文件API客户端
     */
    public void setFileApiClient(FileApiClient fileApiClient) {
        this.fileApiClient = fileApiClient;
    }

    public void setCompressionThreshold(long thresholdBytes) {
        this.compressionService.setThresholdBytes(thresholdBytes);
    }

    public void setHashChunkSize(int chunkSizeBytes) {
        this.hashCalculator.setChunkSizeBytes(chunkSizeBytes);
    }

    /**
     * 启动文件监听
     * 注册事件监听器并启动后台监听线程
     */
    public void startWatching() {
        if (!watching) {
            Path root = directoryWatcher.getWatchDir();
            if (root == null) {
                log.warn("未设置监听目录，同步管理器无法启动");
                return;
            }
            watching = true;
            log.info("启动文件监听服务");
            directoryWatcher.setEventListener(this::handleLocalEvent);
            directoryWatcher.start();
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
                    handleFileDeletion(filePath);
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
            
            if (!Files.exists(filePath)) {
                log.warn("文件不存在，跳过上传: {}", filePath);
                return;
            }
            if (Files.isDirectory(filePath)) {
                log.debug("跳过目录: {}", filePath);
                return;
            }

            String contentHash = hashCalculator.computeFileHash(filePath);
            if (contentHash == null) {
                log.error("计算文件哈希值失败: {}", filePath);
                return;
            }
            byte[] payload = compressionService.compress(filePath);
            if (payload == null) {
                log.error("文件压缩失败: {}", filePath);
                return;
            }
            Path root = directoryWatcher.getWatchDir();
            String relativePath = toRelativePath(root, filePath);

            FileUploadRequest request = new FileUploadRequest();
            request.setLocalPath(filePath);
            request.setFilePath(relativePath);
            request.setCompressedPayload(payload);
            request.setContentHash(contentHash);

            executorService.submit(() -> uploadWithRetry(filePath, request));
        } catch (Exception e) {
            log.error("准备文件上传时发生错误: {}", filePath, e);
        }
    }

    private void uploadWithRetry(Path filePath, FileUploadRequest request) {
        if (fileApiClient == null) {
            log.warn("FileApiClient 未初始化，无法上传文件");
            return;
        }
        try {
            retryTemplate.execute(() -> {
                if (!fileApiClient.uploadFile(request)) {
                    throw new IOException("文件上传失败: " + request.getFilePath());
                }
                return Boolean.TRUE;
            });
            log.info("文件上传完成: {}", filePath);
        } catch (Exception e) {
            log.error("文件上传重试仍失败: {}", filePath, e);
        }
    }

    private void handleFileDeletion(Path filePath) {
        if (fileApiClient == null) {
            log.warn("FileApiClient 未初始化，无法删除远程文件");
            return;
        }
        Path root = directoryWatcher.getWatchDir();
        String relativePath = toRelativePath(root, filePath);
        executorService.submit(() -> {
            try {
                retryTemplate.execute(() -> {
                    if (!fileApiClient.deleteFile(relativePath)) {
                        throw new IOException("远程删除失败: " + relativePath);
                    }
                    return Boolean.TRUE;
                });
                log.info("远程删除文件成功: {}", relativePath);
            } catch (Exception e) {
                log.error("远程删除文件失败: {}", relativePath, e);
            }
        });
    }

    /**
     * 同步远程变更
     * 从服务器获取变更并应用到本地
     */
    public void synchronizeRemoteChanges() {
        try {
            log.debug("开始同步远程变更");
            if (fileApiClient == null) {
                log.warn("FileApiClient 未初始化，无法执行远程同步");
                return;
            }
            Path root = directoryWatcher.getWatchDir();
            if (root == null) {
                log.warn("未设置监听目录，跳过远程同步");
                return;
            }
            List<FileMetadata> remoteFiles = Optional.ofNullable(fileApiClient.listFiles())
                .orElseGet(Collections::emptyList);
            Map<String, FileMetadata> remoteByPath = remoteFiles.stream()
                .collect(Collectors.toMap(
                    meta -> normalizeRemotePath(meta.getFilePath()),
                    meta -> meta,
                    (left, right) -> right));
            Set<String> processed = new HashSet<>();

            Files.walk(root)
                .filter(Files::isRegularFile)
                .forEach(local -> {
                    String relative = toRelativePath(root, local);
                    FileMetadata remote = remoteByPath.get(relative);
                    if (remote == null) {
                        handleFileUpload(local);
                    } else {
                        processed.add(relative);
                        reconcileLocalWithRemote(local, remote);
                    }
                });

            for (Map.Entry<String, FileMetadata> entry : remoteByPath.entrySet()) {
                if (processed.contains(entry.getKey())) {
                    continue;
                }
                Path target = root.resolve(entry.getKey()).normalize();
                try {
                    FileUtils.ensureParentDirectory(target);
                    downloadRemoteFile(entry.getValue(), target);
                } catch (IOException e) {
                    log.error("准备下载远程文件失败: {}", target, e);
                }
            }

            log.debug("远程变更同步完成");
        } catch (Exception e) {
            log.error("同步远程变更时发生错误", e);
        }
    }

    private void reconcileLocalWithRemote(Path local, FileMetadata remote) {
        try {
            String localHash = hashCalculator.computeFileHash(local);
            if (localHash != null && localHash.equals(remote.getContentHash())) {
                return;
            }

            ConflictResolver.ConflictResolutionResult resolution =
                conflictResolver.resolve(local, remote);

            if (!resolution.downloadRemote()) {
                log.debug("根据策略保留本地文件: {}", local);
                return;
            }

            if (resolution.requiresBackup()) {
                Path backup = resolution.localBackup();
                FileUtils.ensureParentDirectory(backup);
                Files.move(local, backup, StandardCopyOption.REPLACE_EXISTING);
                log.info("本地文件已备份到: {}", backup);
            }

            downloadRemoteFile(remote, resolution.remoteTarget());
        } catch (IOException e) {
            log.error("处理本地文件与远程文件冲突失败: {}", local, e);
        }
    }

    private void downloadRemoteFile(FileMetadata metadata, Path target) {
        executorService.submit(() -> {
            try {
                retryTemplate.execute(() -> {
                    if (!fileApiClient.downloadFile(metadata.getFileId(), target)) {
                        throw new IOException("远程下载失败: " + metadata.getFileId());
                    }
                    return Boolean.TRUE;
                });
                log.info("远程文件下载完成: {} -> {}", metadata.getFileId(), target);
            } catch (Exception e) {
                log.error("远程文件下载失败: {}", metadata.getFileId(), e);
            }
        });
    }

    private String toRelativePath(Path root, Path absolute) {
        if (root == null) {
            return absolute.toString();
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalized = absolute.toAbsolutePath().normalize();
        Path relative = normalizedRoot.relativize(normalized);
        return normalizeRemotePath(relative.toString());
    }

    private String normalizeRemotePath(String path) {
        return path.replace("\\", "/");
    }
}