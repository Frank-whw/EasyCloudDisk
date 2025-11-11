package com.clouddisk.file.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotifyUploadRequest {
    
    @NotBlank(message = "内容哈希不能为空")
    private String contentHash;
    
    @NotBlank(message = "文件路径不能为空")
    private String filePath;
}