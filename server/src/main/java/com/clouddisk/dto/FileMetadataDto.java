package com.clouddisk.dto;

import lombok.Data;

import java.time.Instant;

/**
 * 对外暴露的文件元数据视图对象。
 */
@Data
public class FileMetadataDto {
    private String fileId;
    private String name;
    private String path;
    private long size;
    private boolean directory;
    private String hash;
    private int version;
    private Instant updatedAt;

}
