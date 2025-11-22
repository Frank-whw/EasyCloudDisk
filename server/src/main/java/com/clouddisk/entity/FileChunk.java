package com.clouddisk.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 文件块实体,用于块级去重存储。
 * 相同内容的块只存储一次,多个文件可共享同一个块。
 */
@Entity
@Table(name = "file_chunks", indexes = {
        @Index(name = "idx_chunk_hash", columnList = "chunkHash", unique = true),
        @Index(name = "idx_storage_key", columnList = "storageKey")
})
@Data
public class FileChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long chunkId;

    /**
     * 块内容的 SHA-256 哈希值,用于去重。
     */
    @Column(nullable = false, length = 64)
    private String chunkHash;

    /**
     * OSS 存储键。
     */
    @Column(nullable = false, length = 512)
    private String storageKey;

    /**
     * 块大小(字节)。
     */
    @Column(nullable = false)
    private Long chunkSize;

    /**
     * 是否已压缩。
     */
    @Column(nullable = false)
    private Boolean compressed = false;

    /**
     * 引用计数,记录有多少文件引用了该块。
     * 当引用计数为0时可以删除该块。
     */
    @Column(nullable = false)
    private Integer refCount = 1;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 增加引用计数。
     */
    public void incrementRef() {
        this.refCount++;
    }

    /**
     * 减少引用计数。
     */
    public void decrementRef() {
        if (this.refCount > 0) {
            this.refCount--;
        }
    }
}
