package com.clouddisk.dto;

import lombok.Data;

/**
 * 冲突解决请求 DTO
 */
@Data
public class ConflictResolutionRequest {
    
    /**
     * 冲突解决策略：OVERWRITE（覆盖）、CREATE_COPY（创建副本）、MERGE（合并）
     */
    private String strategy;
    
    /**
     * 期望的版本号（用于乐观锁检查）
     */
    private Long expectedVersion;
    
    /**
     * 新文件名（当策略为 CREATE_COPY 时使用）
     */
    private String newFileName;
    
    /**
     * 合并后的内容（当策略为 MERGE 时使用）
     */
    private String mergedContent;
}
