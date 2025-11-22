package com.clouddisk.client.sync;

import com.clouddisk.client.model.FileMetadata;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

/**
 * 冲突解决器
 * 用于处理本地文件与远程文件之间的冲突
 */
@Slf4j
public class ConflictResolver {
    
    /**
     * 解决文件冲突的策略枚举
     */
    public enum ConflictResolutionStrategy {
        /**
         * 使用本地文件版本
         */
        USE_LOCAL,
        
        /**
         * 使用远程文件版本
         */
        USE_REMOTE,
        
        /**
         * 保留两个版本，重命名本地文件
         */
        KEEP_BOTH,
        
        /**
         * 根据修改时间决定使用哪个版本
         */
        USE_NEWER
    }
    
    /**
     * 默认冲突解决策略
     */
    private ConflictResolutionStrategy defaultStrategy = ConflictResolutionStrategy.USE_NEWER;
    
    /**
     * 构造函数，使用默认策略
     */
    public ConflictResolver() {
    }
    
    /**
     * 构造函数，指定默认策略
     * @param defaultStrategy 默认冲突解决策略
     */
    public ConflictResolver(ConflictResolutionStrategy defaultStrategy) {
        this.defaultStrategy = defaultStrategy;
    }
    
    /**
     * 解决文件冲突
     * @param local 本地文件路径
     * @param remote 远程文件元数据
     * @return 解决冲突后的文件路径
     */
    public Path resolve(Path local, FileMetadata remote) {
        return resolve(local, remote, defaultStrategy);
    }
    
    /**
     * 解决文件冲突
     * @param local 本地文件路径
     * @param remote 远程文件元数据
     * @param strategy 冲突解决策略
     * @return 解决冲突后的文件路径
     */
    public Path resolve(Path local, FileMetadata remote, ConflictResolutionStrategy strategy) {
        log.debug("开始解决文件冲突: local={}, remote={}, strategy={}", 
                  local, remote.getFileName(), strategy);
        
        switch (strategy) {
            case USE_LOCAL:
                return resolveUseLocal(local, remote);
                
            case USE_REMOTE:
                return resolveUseRemote(local, remote);
                
            case KEEP_BOTH:
                return resolveKeepBoth(local, remote);
                
            case USE_NEWER:
                return resolveUseNewer(local, remote);
                
            default:
                log.warn("未知的冲突解决策略: {}, 使用默认策略", strategy);
                return resolve(local, remote, defaultStrategy);
        }
    }
    
    /**
     * 使用本地文件版本解决冲突
     * @param local 本地文件路径
     * @param remote 远程文件元数据
     * @return 本地文件路径
     */
    private Path resolveUseLocal(Path local, FileMetadata remote) {
        log.debug("使用本地文件版本解决冲突: {}", local.getFileName());
        // 直接返回本地文件路径，表示保留本地版本
        return local;
    }
    
    /**
     * 使用远程文件版本解决冲突
     * @param local 本地文件路径
     * @param remote 远程文件元数据
     * @return 远程文件应保存到的本地路径
     */
    private Path resolveUseRemote(Path local, FileMetadata remote) {
        log.debug("使用远程文件版本解决冲突: {}", remote.getFileName());
        // 返回本地文件路径，表示应该用远程文件覆盖本地文件
        return local;
    }
    
    /**
     * 保留两个版本解决冲突
     * @param local 本地文件路径
     * @param remote 远程文件元数据
     * @return 重命名后的本地文件路径
     */
    private Path resolveKeepBoth(Path local, FileMetadata remote) {
        log.debug("保留两个版本解决冲突: local={}, remote={}", 
                  local.getFileName(), remote.getFileName());
        
        // 生成新的文件名，添加时间戳以避免冲突
        String fileName = local.getFileName().toString();
        String newName = generateUniqueFileName(fileName);
        return local.getParent().resolve(newName);
    }
    
    /**
     * 根据修改时间使用较新版本解决冲突
     * @param local 本地文件路径
     * @param remote 远程文件元数据
     * @return 应该保留的文件路径
     */
    private Path resolveUseNewer(Path local, FileMetadata remote) {
        log.debug("根据修改时间使用较新版本解决冲突: local={}, remote={}", 
                  local.getFileName(), remote.getFileName());
        
        // TODO: 获取本地文件的最后修改时间
        // 这里需要实际获取本地文件的最后修改时间
        long localLastModified = System.currentTimeMillis(); // 临时值，需要替换为实际值
        long remoteLastModified = remote.getLastModified();
        
        // 比较两个文件的修改时间
        if (localLastModified >= remoteLastModified) {
            log.debug("本地文件较新，保留本地版本");
            return local; // 本地文件较新，保留本地版本
        } else {
            log.debug("远程文件较新，使用远程版本");
            return local; // 远程文件较新，应该用远程文件覆盖本地文件
        }
    }
    
    /**
     * 生成唯一的文件名
     * @param originalName 原始文件名
     * @return 带时间戳的唯一文件名
     */
    private String generateUniqueFileName(String originalName) {
        // 获取文件扩展名
        String extension = "";
        String nameWithoutExtension = originalName;
        
        int lastDotIndex = originalName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = originalName.substring(lastDotIndex);
            nameWithoutExtension = originalName.substring(0, lastDotIndex);
        }
        
        // 添加时间戳生成唯一文件名
        long timestamp = System.currentTimeMillis();
        return nameWithoutExtension + "_" + timestamp + extension;
    }
    
    /**
     * 设置默认冲突解决策略
     * @param strategy 冲突解决策略
     */
    public void setDefaultStrategy(ConflictResolutionStrategy strategy) {
        this.defaultStrategy = strategy;
    }
    
    /**
     * 获取默认冲突解决策略
     * @return 默认冲突解决策略
     */
    public ConflictResolutionStrategy getDefaultStrategy() {
        return defaultStrategy;
    }
}