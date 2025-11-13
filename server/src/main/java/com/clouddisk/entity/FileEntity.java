package com.clouddisk.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 文件实体，记录用户文件与对应的存储信息。
 */
@Entity
@Data
@Table(name = "files", indexes = {
        @Index(name = "idx_files_user_path", columnList = "user_id,directory_path,name", unique = true),
        @Index(name = "idx_files_hash", columnList = "content_hash")
})
public class FileEntity {

    @Id
    @Column(name = "file_id", nullable = false, updatable = false, length = 36)
    private String fileId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "directory_path", nullable = false)
    private String directoryPath;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "is_directory", nullable = false)
    private boolean directory;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

}