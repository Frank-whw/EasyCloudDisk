package com.clouddisk.dto;

import lombok.Data;

import java.time.Instant;

/**
 * 文件版本元数据视图。
 */
@Data
public class FileVersionDto {
    private int versionNumber;
    private long fileSize;
    private String contentHash;
    private Instant createdAt;
}

