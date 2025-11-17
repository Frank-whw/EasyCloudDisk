package com.clouddisk.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 秒传请求DTO
 */
@Data
public class QuickUploadRequest {
    
    @NotBlank(message = "文件哈希不能为空")
    private String hash;
    
    @NotBlank(message = "文件名不能为空")
    private String fileName;
    
    private String path;
    
    private Long fileSize;
}
