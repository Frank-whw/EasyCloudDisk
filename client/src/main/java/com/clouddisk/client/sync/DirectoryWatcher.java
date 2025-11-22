package com.clouddisk.client.sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 基于 {@link WatchService} 的目录监听器，用于捕获本地文件系统变化并回调上层处理逻辑。
 */
@Slf4j
@Component
public class DirectoryWatcher {
    private WatchService watchService;
    private ExecutorService executorService;
    private Consumer<FileEvent> eventListener;
    private volatile boolean watching = false;
    private Path watchDir;

    public DirectoryWatcher() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.executorService = Executors.newSingleThreadExecutor();
        } catch (IOException e) {
            log.error("创建文件监听服务失败", e);
        }
    }
    
    /**
     * 启动监听，将监听任务提交至后台线程。
     */
    public void start() {
        if (!watching && watchService != null) {
            watching = true;
            log.info("启动目录监听: {}", watchDir != null ? watchDir.toString() : "未设置监听目录");
            
            // 在后台线程中监听文件变化
            executorService.submit(this::watchDirectory);
        }
    }
    
    /**
     * 停止监听并释放底层资源。
     */
    public void stop() {
        if (watching) {
            log.info("停止目录监听");
            watching = false;
            
            // 关闭监听服务
            if (watchService != null) {
                try {
                    watchService.close();
                } catch (IOException e) {
                    log.error("关闭监听服务时发生错误", e);
                }
            }
            
            // 关闭线程池
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
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
     * 设置监听目录
     * @param dir 目录路径
     * @throws IOException IO异常
     */
    public void setWatchDir(Path dir) throws IOException {
        this.watchDir = dir;
        if (watchService != null && watchDir != null) {
            watchDir.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
            log.info("已设置监听目录: {}", watchDir.toString());
        }
    }
    
    /**
     * 设置事件监听器
     * @param eventListener 事件处理器
     */
    public void setEventListener(Consumer<FileEvent> eventListener) {
        this.eventListener = eventListener;
    }
    
    /**
     * 监听目录变化的核心方法，从 {@link WatchService} 轮询事件并转发给监听器。
     */
    private void watchDirectory() {
        if (watchService == null || watchDir == null) {
            log.warn("监听服务或监听目录未正确设置");
            return;
        }
        
        try {
            while (watching) {
                WatchKey key = watchService.take();
                
                for (WatchEvent<?> watchEvent : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = watchEvent.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) watchEvent;
                    Path fileName = pathEvent.context();
                    Path filePath = watchDir.resolve(fileName);
                    
                    FileEvent.EventType eventType;
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        eventType = FileEvent.EventType.CREATE;
                    } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        eventType = FileEvent.EventType.MODIFY;
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        eventType = FileEvent.EventType.DELETE;
                    } else {
                        eventType = FileEvent.EventType.UNKNOWN;
                    }
                    
                    FileEvent fileEvent = new FileEvent(eventType, filePath, pathEvent);
                    
                    if (eventListener != null) {
                        eventListener.accept(fileEvent);
                    }
                }
                
                boolean valid = key.reset();
                if (!valid) {
                    log.warn("监听键失效，停止监听");
                    break;
                }
            }
        } catch (InterruptedException e) {
            log.info("目录监听被中断");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("目录监听过程中发生错误", e);
        }
        
        log.info("目录监听已停止");
    }
}