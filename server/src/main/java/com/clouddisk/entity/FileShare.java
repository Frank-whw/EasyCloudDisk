package com.clouddisk.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 文件共享实体，记录文件的共享信息。
 */
@Entity
@Data
@Table(name = "file_shares", indexes = {
        @Index(name = "idx_file_shares_share_id", columnList = "share_id", unique = true),
        @Index(name = "idx_file_shares_file_id", columnList = "file_id"),
        @Index(name = "idx_file_shares_owner", columnList = "owner_id")
})
public class FileShare {

    @Id
    @Column(name = "share_id", nullable = false, updatable = false, length = 36)
    private String shareId;

    @Column(name = "file_id", nullable = false, length = 36)
    private String fileId;

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "share_name")
    private String shareName;

    @Column(name = "share_description")
    private String shareDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false)
    private SharePermission permission;

    @Column(name = "password")
    private String password; // 可选的访问密码

    @Column(name = "expires_at")
    private Instant expiresAt; // 可选的过期时间

    @Column(name = "download_count", nullable = false)
    private Long downloadCount = 0L;

    @Column(name = "max_downloads")
    private Long maxDownloads; // 可选的最大下载次数

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (shareId == null) {
            shareId = java.util.UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * 共享权限枚举
     */
    public enum SharePermission {
        READ_ONLY,    // 只读
        READ_WRITE,   // 读写
        DOWNLOAD_ONLY // 仅下载
    }
}
