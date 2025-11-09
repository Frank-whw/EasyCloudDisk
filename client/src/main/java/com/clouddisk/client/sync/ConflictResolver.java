package com.clouddisk.client.sync;

import com.clouddisk.client.model.FileMetadata;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 冲突解决器，用于确定本地与远程文件冲突时的处理策略。
 */
@Slf4j
public class ConflictResolver {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS").withZone(ZoneOffset.UTC);

    public enum ConflictResolutionStrategy {
        USE_LOCAL,
        USE_REMOTE,
        KEEP_BOTH,
        USE_NEWER
    }

    private ConflictResolutionStrategy defaultStrategy = ConflictResolutionStrategy.USE_NEWER;

    public ConflictResolver() {
    }

    public ConflictResolver(ConflictResolutionStrategy defaultStrategy) {
        this.defaultStrategy = Objects.requireNonNull(defaultStrategy);
    }

    public ConflictResolutionResult resolve(Path local, FileMetadata remote) {
        return resolve(local, remote, defaultStrategy);
    }

    public ConflictResolutionResult resolve(Path local,
                                            FileMetadata remote,
                                            ConflictResolutionStrategy strategy) {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(strategy, "strategy");

        log.debug("解决文件冲突: local={}, remote={}, strategy={}",
            local, remote.getFileName(), strategy);

        return switch (strategy) {
            case USE_LOCAL -> keepLocalOnly(local, strategy);
            case USE_REMOTE -> useRemote(local, strategy);
            case KEEP_BOTH -> keepBoth(local, strategy);
            case USE_NEWER -> useNewer(local, remote, strategy);
        };
    }

    private ConflictResolutionResult keepLocalOnly(Path local,
                                                   ConflictResolutionStrategy strategy) {
        return new ConflictResolutionResult(false, local, null, strategy);
    }

    private ConflictResolutionResult useRemote(Path local,
                                               ConflictResolutionStrategy strategy) {
        return new ConflictResolutionResult(true, local, null, strategy);
    }

    private ConflictResolutionResult keepBoth(Path local,
                                              ConflictResolutionStrategy strategy) {
        Path backupPath = generateConflictPath(local);
        return new ConflictResolutionResult(true, local, backupPath, strategy);
    }

    private ConflictResolutionResult useNewer(Path local,
                                              FileMetadata remote,
                                              ConflictResolutionStrategy strategy) {
        long localLastModified = safeLastModified(local);
        long remoteLastModified = remote.getLastModified();
        if (localLastModified >= remoteLastModified) {
            log.debug("本地较新(本地: {}, 远程: {})，保留本地", localLastModified, remoteLastModified);
            return keepLocalOnly(local, strategy);
        }
        log.debug("远程较新(本地: {}, 远程: {})，使用远程版本", localLastModified, remoteLastModified);
        return useRemote(local, strategy);
    }

    private Path generateConflictPath(Path local) {
        String fileName = local.getFileName().toString();
        String extension = "";
        String nameWithoutExtension = fileName;
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = fileName.substring(lastDotIndex);
            nameWithoutExtension = fileName.substring(0, lastDotIndex);
        }
        Path parent = local.getParent();
        String timestamp = TIMESTAMP_FORMATTER.format(Instant.now());
        Path candidate = parent.resolve(nameWithoutExtension + "_conflict_" + timestamp + extension);
        int counter = 1;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(nameWithoutExtension + "_conflict_" + timestamp + "_" + counter + extension);
            counter++;
        }
        return candidate;
    }

    private long safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            log.warn("获取文件修改时间失败: {}", path, e);
            return 0L;
        }
    }

    public void setDefaultStrategy(ConflictResolutionStrategy strategy) {
        this.defaultStrategy = Objects.requireNonNull(strategy);
    }

    public ConflictResolutionStrategy getDefaultStrategy() {
        return defaultStrategy;
    }

    public record ConflictResolutionResult(boolean downloadRemote,
                                           Path remoteTarget,
                                           Path localBackup,
                                           ConflictResolutionStrategy appliedStrategy) {
        public boolean requiresBackup() {
            return localBackup != null;
        }
    }
}