package com.clouddisk.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 文件加密元数据实体
 */
@Data
@Entity
@Table(name = "file_encryption_metadata", indexes = {
    @Index(name = "idx_file_id", columnList = "file_id")
})
public class FileEncryptionMetadata {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "file_id", nullable = false, length = 36)
    private String fileId;
    
    @Column(name = "algorithm", nullable = false, length = 50)
    private String algorithm; // AES-256-GCM, AES-256-CBC
    
    @Column(name = "key_derivation", length = 50)
    private String keyDerivation; // PBKDF2, Argon2
    
    @Column(name = "salt", columnDefinition = "TEXT")
    private String salt; // Base64编码的盐值
    
    @Column(name = "iterations")
    private Integer iterations;
    
    @Column(name = "iv", columnDefinition = "TEXT")
    private String iv; // 初始化向量（Base64编码）
    
    @Column(name = "convergent")
    private Boolean convergent = false; // 是否使用收敛加密
    
    @Column(name = "original_size")
    private Long originalSize; // 原始文件大小
    
    @Column(name = "encrypted_size")
    private Long encryptedSize; // 加密后大小
    
    @Column(name = "original_hash", length = 64)
    private String originalHash; // 原始文件哈希
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "client_encrypted")
    private Boolean clientEncrypted = true; // 是否客户端加密
}
