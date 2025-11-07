package com.clouddisk.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/*
  表结构
  CREATE TABLE files (
      file_id VARCHAR(36) PRIMARY KEY,
      user_id VARCHAR(36) NOT NULL,
      name VARCHAR(255) NOT NULL,  -- 文件名建议指定长度
      file_path VARCHAR(1024),     -- 文件路径（可选，根据需求）
      s3_key VARCHAR(1024) NOT NULL,         -- S3存储键
      file_size BIGINT,
      content_hash VARCHAR(64),              -- 用于去重（如MD5/SHA256）
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      -- 关联users表的外键（确保file属于存在的用户，可选但推荐）
      FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
  );
 */

/**
 * 文件实体类
 *
 */
@Entity
@Table(name = "files")
@Data
public class FileEntity {
    @Id
    private String file_id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "file_path", length = 1024)
    private String filePath;

    @Column(name = "s3_key", nullable = false, length = 1024)
    private String s3Key;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}