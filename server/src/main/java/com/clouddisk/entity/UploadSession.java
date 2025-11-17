package com.clouddisk.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 断点续传会话实体
 */
@Data
@Entity
@Table(name = "upload_sessions", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class UploadSession {
    
    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId;
    
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;
    
    @Column(name = "file_name", nullable = false)
    private String fileName;
    
    @Column(name = "file_path")
    private String filePath;
    
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    
    @Column(name = "file_hash")
    private String fileHash;
    
    @Column(name = "total_chunks", nullable = false)
    private Integer totalChunks;
    
    @Column(name = "chunk_size", nullable = false)
    private Integer chunkSize;
    
    @ElementCollection
    @CollectionTable(
        name = "upload_session_chunks",
        joinColumns = @JoinColumn(name = "session_id")
    )
    @Column(name = "chunk_index")
    private Set<Integer> uploadedChunks = new HashSet<>();
    
    @Column(name = "status", length = 20)
    private String status = "ACTIVE"; // ACTIVE, COMPLETED, EXPIRED
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @PrePersist
    public void prePersist() {
        if (sessionId == null) {
            sessionId = java.util.UUID.randomUUID().toString();
        }
        if (expiresAt == null) {
            // 24小时后过期
            expiresAt = LocalDateTime.now().plusHours(24);
        }
    }
}
