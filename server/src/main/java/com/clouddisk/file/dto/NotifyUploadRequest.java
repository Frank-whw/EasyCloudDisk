package com.clouddisk.file.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotifyUploadRequest {
    
    @NotBlank(message = "内容哈希不能为空")
    private String contentHash;
    
    @NotBlank(message = "文件路径不能为空")
    private String filePath;

    // 可选：客户端上报的文件大小（用于直传通知时的服务器端大小校验）
    @Positive(message = "文件大小必须为正数")
    private Long fileSize;
}