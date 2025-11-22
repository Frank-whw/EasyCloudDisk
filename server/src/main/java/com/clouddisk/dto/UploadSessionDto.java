package com.clouddisk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 断点续传会话信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadSessionDto {
    
    private String sessionId;
    
    private String fileName;
    
    private Long fileSize;
    
    private Integer totalChunks;
    
    private Integer uploadedChunks;
    
    private String status; // ACTIVE, COMPLETED, EXPIRED
}
