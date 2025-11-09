package com.clouddisk.client.sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
@Component
public class DirectoryWatcher {
    private final Object lifecycleMonitor = new Object();
    private final Map<Path, WatchKey> registeredKeys = new ConcurrentHashMap<>();
    private final Map<Path, DebounceTask> pendingEvents = new ConcurrentHashMap<>();

    private WatchService watchService;
    private ExecutorService callbackExecutor;
    private ScheduledExecutorService debounceExecutor;
    private Thread watchThread;

    private Consumer<FileEvent> eventListener;
    private volatile boolean watching = false;
    private Path watchDir;
    private long debounceMillis = 500L;

    public DirectoryWatcher() {
        initializeExecutors();
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            log.error("创建文件监听服务失败", e);
        }
    }

    private void initializeExecutors() {
        this.callbackExecutor = Executors.newFixedThreadPool(
            Math.max(Runtime.getRuntime().availableProcessors() / 2, 1),
            runnable -> {
                Thread thread = new Thread(runnable, "directory-watcher-callback");
                thread.setDaemon(true);
                return thread;
            }
        );
        this.debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "directory-watcher-debounce");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 设置事件防抖时间，单位毫秒。
     */
    public void setDebounceMillis(long debounceMillis) {
        if (debounceMillis < 0) {
            throw new IllegalArgumentException("debounceMillis must be >= 0");
        }
        this.debounceMillis = debounceMillis;
    }

    /**
     * 设置监听目录。
     */
    public void setWatchDir(Path dir) throws IOException {
        Objects.requireNonNull(dir, "dir");
        Path normalized = dir.toAbsolutePath().normalize();
        this.watchDir = normalized;
        if (watchService == null) {
            this.watchService = FileSystems.getDefault().newWatchService();
        }
        registeredKeys.clear();
        registerRecursive(normalized);
    }

    public Path getWatchDir() {
        return watchDir;
    }

    /**
     * 设置事件回调。
     */
    public void setEventListener(Consumer<FileEvent> eventListener) {
        this.eventListener = eventListener;
    }

    /**
     * 启动监听。
     */
    public void start() {
        synchronized (lifecycleMonitor) {
            if (watching) {
                return;
            }
            if (watchDir == null) {
                log.warn("未设置监听目录，无法启动 DirectoryWatcher");
                return;
            }
            try {
                if (watchService == null) {
                    watchService = FileSystems.getDefault().newWatchService();
                }
                registerRecursive(watchDir);
            } catch (IOException e) {
                throw new IllegalStateException("注册监听目录失败: " + watchDir, e);
            }
            watching = true;
            watchThread = new Thread(this::watchLoop, "directory-watcher-loop");
            watchThread.setDaemon(true);
            watchThread.start();
            log.info("启动目录监听: {}", watchDir);
        }
    }

    /**
     * 停止监听。
     */
    public void stop() {
        synchronized (lifecycleMonitor) {
            if (!watching) {
                return;
            }
            watching = false;
            log.info("停止目录监听: {}", watchDir);
            if (watchThread != null) {
                watchThread.interrupt();
                watchThread = null;
            }
            closeWatchService();
            registeredKeys.clear();
            pendingEvents.values().forEach(DebounceTask::cancel);
            pendingEvents.clear();
        }
    }

    private void closeWatchService() {
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("关闭 WatchService 时发生异常", e);
            }
        }
        watchService = null;
    }

    /**
     * 关闭内部线程池（用于应用整体关闭）。
     */
    public void shutdownExecutors() {
        callbackExecutor.shutdownNow();
        debounceExecutor.shutdownNow();
        synchronized (lifecycleMonitor) {
            watching = false;
            closeWatchService();
        }
    }

    private void watchLoop() {
        while (watching) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }

            Path dir = (Path) key.watchable();
            for (WatchEvent<?> event : key.pollEvents()) {
                handleWatchEvent(dir, event);
            }

            boolean valid = key.reset();
            if (!valid) {
                registeredKeys.values().removeIf(existing -> existing.equals(key));
            }
        }
        log.info("目录监听线程退出");
    }

    @SuppressWarnings("unchecked")
    private void handleWatchEvent(Path dir, WatchEvent<?> rawEvent) {
        WatchEvent.Kind<?> kind = rawEvent.kind();
        if (kind == StandardWatchEventKinds.OVERFLOW) {
            return;
        }

        WatchEvent<Path> watchEvent = (WatchEvent<Path>) rawEvent;
        Path relativePath = watchEvent.context();
        Path absolutePath = dir.resolve(relativePath).normalize();
        boolean directory = Files.isDirectory(absolutePath);

        if (kind == StandardWatchEventKinds.ENTRY_CREATE && directory) {
            try {
                registerRecursive(absolutePath);
            } catch (IOException e) {
                log.warn("注册新目录监听失败: {}", absolutePath, e);
            }
        }

        FileEvent.EventType eventType = toEventType(kind);
        FileEvent fileEvent = new FileEvent(eventType, absolutePath, watchEvent, directory, watchDir, null);
        dispatchEvent(absolutePath, fileEvent);
    }

    private FileEvent.EventType toEventType(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return FileEvent.EventType.CREATE;
        }
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            return FileEvent.EventType.MODIFY;
        }
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return FileEvent.EventType.DELETE;
        }
        return FileEvent.EventType.UNKNOWN;
    }

    private void dispatchEvent(Path path, FileEvent event) {
        if (eventListener == null) {
            return;
        }
        if (debounceMillis <= 0) {
            callbackExecutor.submit(() -> eventListener.accept(event));
            return;
        }
        pendingEvents.compute(path, (p, existing) -> {
            if (existing != null) {
                existing.cancel();
            }
            ScheduledFuture<?> future = debounceExecutor.schedule(() -> {
                pendingEvents.remove(p);
                callbackExecutor.submit(() -> eventListener.accept(event));
            }, debounceMillis, TimeUnit.MILLISECONDS);
            return new DebounceTask(event, future);
        });
    }

    private void registerRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            log.warn("尝试注册的目录不存在: {}", root);
            return;
        }
        if (watchService == null) {
            throw new IllegalStateException("watchService 未初始化");
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerDirectory(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void registerDirectory(Path dir) throws IOException {
        Path normalized = dir.toAbsolutePath().normalize();
        if (registeredKeys.containsKey(normalized)) {
            return;
        }
        WatchKey key = normalized.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE
        );
        registeredKeys.put(normalized, key);
        log.debug("注册目录监听: {}", normalized);
    }

    private static final class DebounceTask {
        private final FileEvent event;
        private final ScheduledFuture<?> future;

        private DebounceTask(FileEvent event, ScheduledFuture<?> future) {
            this.event = event;
            this.future = future;
        }

        void cancel() {
            future.cancel(false);
        }

        @Override
        public String toString() {
            return "DebounceTask{" +
                "event=" + event +
                ", deadline=" + Instant.now() +
                '}';
        }
    }
}