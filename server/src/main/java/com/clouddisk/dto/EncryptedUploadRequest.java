package com.clouddisk.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 加密上传请求DTO
 */
@Data
public class EncryptedUploadRequest {
    
    @NotBlank(message = "文件名不能为空")
    private String fileName;
    
    private String path;
    
    @NotBlank(message = "加密算法不能为空")
    private String algorithm; // AES-256-GCM, AES-256-CBC
    
    private String keyDerivation; // PBKDF2, Argon2
    
    private String salt; // 用于密钥派生的盐值（Base64编码）
    
    private Integer iterations; // 密钥派生迭代次数
    
    private Boolean convergent; // 是否使用收敛加密（支持去重）
    
    private String iv; // 初始化向量（Base64编码）
    
    private Long originalSize; // 原始文件大小（加密前）
    
    private Long encryptedSize; // 加密后文件大小
    
    private String originalHash; // 原始文件哈希（用于完整性校验）
}
