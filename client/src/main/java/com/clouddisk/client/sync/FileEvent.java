package com.clouddisk.client.sync;

import lombok.Data;
import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * 文件事件类
 * 用于封装文件系统中发生的各种事件，如创建、修改、删除等
 */
@Data
public class FileEvent {
    /**
     * 事件类型
     */
    private EventType eventType;
    
    /**
     * 受影响的文件路径
     */
    private Path filePath;
    
    /**
     * 事件发生的时间戳
     */
    private long timestamp;
    
    /**
     * 原始的WatchEvent事件
     */
    private WatchEvent<Path> watchEvent;
    
    /**
     * 构造函数
     * @param eventType 事件类型
     * @param filePath 文件路径
     * @param watchEvent 原始WatchEvent
     */
    public FileEvent(EventType eventType, Path filePath, WatchEvent<Path> watchEvent) {
        this.eventType = eventType;
        this.filePath = filePath;
        this.watchEvent = watchEvent;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 事件类型枚举
     */
    public enum EventType {
        /**
         * 文件创建事件
         */
        CREATE,
        
        /**
         * 文件修改事件
         */
        MODIFY,
        
        /**
         * 文件删除事件
         */
        DELETE,
        
        /**
         * 未知事件类型
         */
        UNKNOWN
    }
    
    /**
     * 从WatchEvent创建FileEvent
     * @param watchEvent 原始WatchEvent
     * @param basePath 监听的基础路径
     * @return FileEvent对象
     */
    public static FileEvent fromWatchEvent(WatchEvent<Path> watchEvent, Path basePath) {
        WatchEvent.Kind<?> kind = watchEvent.kind();
        Path filePath = basePath.resolve(watchEvent.context());
        
        EventType eventType;
        if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_CREATE) {
            eventType = EventType.CREATE;
        } else if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY) {
            eventType = EventType.MODIFY;
        } else if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_DELETE) {
            eventType = EventType.DELETE;
        } else {
            eventType = EventType.UNKNOWN;
        }
        
        return new FileEvent(eventType, filePath, watchEvent);
    }
}