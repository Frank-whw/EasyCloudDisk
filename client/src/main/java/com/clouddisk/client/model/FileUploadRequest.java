package com.clouddisk.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

/**
 * 上传文件时客户端内部使用的请求对象。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileUploadRequest {
    private Path localPath;
    private String filePath;
    private byte[] compressedPayload; // 压缩后的文件内容
    private String contentHash;
}