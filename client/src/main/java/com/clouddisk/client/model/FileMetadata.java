package com.clouddisk.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {
    private String fileId;
    private String userId; // 文件所属用户ID, 用于权限控制
    private String fileName;
    private String filePath;
    private String s3_key; // S3存储的key, 用于下载
    private String contentHash;
    private long fileSize;
    private long lastModified; // 最后修改时间，用来确定文件是否被修改
}