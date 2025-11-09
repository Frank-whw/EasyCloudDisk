package com.clouddisk.client.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文件响应DTO
 * 映射服务端返回的文件信息
 */
public class FileResponse {
    
    private UUID fileId;
    private UUID userId;
    private String name;
    private String filePath;
    private String s3Key;
    private Long fileSize;
    private String contentHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 构造函数
    public FileResponse() {}
    
    public FileResponse(UUID fileId, UUID userId, String name, String filePath, 
                       String s3Key, Long fileSize, String contentHash,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.fileId = fileId;
        this.userId = userId;
        this.name = name;
        this.filePath = filePath;
        this.s3Key = s3Key;
        this.fileSize = fileSize;
        this.contentHash = contentHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getter和Setter方法
    public UUID getFileId() {
        return fileId;
    }
    
    public void setFileId(UUID fileId) {
        this.fileId = fileId;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getS3Key() {
        return s3Key;
    }
    
    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getContentHash() {
        return contentHash;
    }
    
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * 格式化文件大小为人类可读格式
     */
    public String getFormattedSize() {
        if (fileSize == null) {
            return "0 B";
        }
        
        long size = fileSize;
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}
