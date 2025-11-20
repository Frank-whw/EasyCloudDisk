package com.clouddisk.dto;

import com.clouddisk.entity.FileShare;
import lombok.Data;

import java.time.Instant;

/**
 * 文件共享信息DTO
 */
@Data
public class FileShareDto {
    private String shareId;
    private String fileId;
    private String fileName;
    private String ownerId;
    private String ownerEmail;
    private String shareName;
    private String shareDescription;
    private FileShare.SharePermission permission;
    private boolean hasPassword;
    private Instant expiresAt;
    private Long downloadCount;
    private Long maxDownloads;
    private boolean active;
    private Instant createdAt;
    private String shareUrl; // 完整的共享链接
}
