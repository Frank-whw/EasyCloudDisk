package com.clouddisk.dto;

import com.clouddisk.entity.FileShare;
import lombok.Data;

import java.time.Instant;

/**
 * 对外暴露的文件元数据视图对象。
 */
@Data
public class FileMetadataDto {
    private String fileId;
    private String name;
    private String path;
    private long size;
    private boolean directory;
    private String hash;
    private int version;
    private Instant updatedAt;
    /**
     * 共享信息：用于前端展示协作态。
     */
    private boolean shared;
    private String shareId;
    private String ownerEmail;
    private FileShare.SharePermission permission;
    private String shareUrl;
    private boolean hasSharePassword;
    private Instant shareExpiresAt;
}
