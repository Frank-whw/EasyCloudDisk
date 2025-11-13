package com.clouddisk.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 文件版本实体，用于记录历史版本的存储信息。
 */
@Entity
@Data
@Table(name = "file_versions")
public class FileVersion {

    @Id
    @Column(name = "version_id", nullable = false, updatable = false, length = 36)
    private String versionId;

    @Column(name = "file_id", nullable = false, length = 36)
    private String fileId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
    }

}