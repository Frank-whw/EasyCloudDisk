package com.clouddisk.client.sync;

import lombok.Data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * 文件事件类
 * 用于封装文件系统中发生的各种事件，如创建、修改、删除等
 */
@Data
public class FileEvent {
    private final EventType eventType;
    private final Path filePath;
    private final long timestamp;
    private final WatchEvent<Path> watchEvent;
    private final boolean directory;
    private final Path rootPath;
    private final Path previousPath;

    public FileEvent(EventType eventType,
                     Path filePath,
                     WatchEvent<Path> watchEvent,
                     boolean directory,
                     Path rootPath,
                     Path previousPath) {
        this.eventType = eventType;
        this.filePath = filePath;
        this.watchEvent = watchEvent;
        this.directory = directory;
        this.rootPath = rootPath;
        this.previousPath = previousPath;
        this.timestamp = System.currentTimeMillis();
    }

    public FileEvent(EventType eventType, Path filePath, WatchEvent<Path> watchEvent) {
        this(eventType, filePath, watchEvent, false, null, null);
    }

    public enum EventType {
        CREATE,
        MODIFY,
        DELETE,
        UNKNOWN
    }

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

        boolean directory = Files.isDirectory(filePath);
        return new FileEvent(eventType, filePath, watchEvent, directory, basePath, null);
    }
}