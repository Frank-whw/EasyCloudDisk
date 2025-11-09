package com.clouddisk.common.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileResponse {
    
    private UUID fileId;
    private UUID userId;
    private String name;
    private String filePath;
    private String s3Key;
    private Long fileSize;
    private String contentHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}