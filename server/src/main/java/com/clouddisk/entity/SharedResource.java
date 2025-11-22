package com.clouddisk.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 文件/目录共享记录。
 */
@Entity
@Data
@Table(name = "shared_resources", indexes = {
        @Index(name = "idx_shared_target", columnList = "target_user_id"),
        @Index(name = "idx_shared_owner", columnList = "owner_id")
})
public class SharedResource {

    @Id
    @Column(name = "share_id", nullable = false, updatable = false, length = 36)
    private String shareId;

    @Column(name = "file_id", nullable = false, length = 36)
    private String fileId;

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "target_user_id", nullable = false, length = 36)
    private String targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 16)
    private SharePermission permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 16)
    private ShareResourceType resourceType;

    @Column(name = "resource_path", nullable = false)
    private String resourcePath;

    @Column(name = "include_subtree", nullable = false)
    private boolean includeSubtree;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @PrePersist
    public void prePersist() {
        if (shareId == null) {
            shareId = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}

