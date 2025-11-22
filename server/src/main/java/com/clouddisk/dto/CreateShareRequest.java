package com.clouddisk.dto;

import com.clouddisk.entity.FileShare;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

/**
 * 创建文件共享请求DTO
 */
@Data
public class CreateShareRequest {
    // fileId从URL路径参数获取,不需要在请求体中验证
    private String fileId;
    
    private String shareName;
    private String shareDescription;
    
    @NotNull(message = "权限类型不能为空")
    private FileShare.SharePermission permission;
    
    private String password; // 可选的访问密码
    private Instant expiresAt; // 可选的过期时间
    private Long maxDownloads; // 可选的最大下载次数
}
