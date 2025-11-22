package com.clouddisk.client.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件响应DTO，对应服务端返回的文件元数据。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileResponse {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private UUID fileId;
    private String name;

    @JsonAlias({"filePath", "path"})
    private String filePath;

    @JsonAlias({"fileSize", "size"})
    private Long fileSize;

    @JsonAlias({"contentHash", "hash"})
    private String contentHash;

    @JsonAlias("directory")
    private Boolean directory;

    @JsonAlias("version")
    private Integer version;

    @JsonAlias("createdAt")
    private Instant createdAt;

    @JsonAlias("updatedAt")
    private Instant updatedAt;

    public FileResponse() {
    }

    public UUID getFileId() {
        return fileId;
    }

    public void setFileId(UUID fileId) {
        this.fileId = fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId != null ? java.util.UUID.fromString(fileId) : null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public void setSize(Long size) {
        this.fileSize = size;
    }

    public void setSize(long size) {
        this.fileSize = size;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Boolean getDirectory() {
        return directory;
    }

    public void setDirectory(Boolean directory) {
        this.directory = directory;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 格式化文件大小为人类可读格式。
     */
    public String getFormattedSize() {
        if (fileSize == null) {
            return "0 B";
        }

        long size = fileSize;
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024L * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 选取更新时间或创建时间作为显示时间。
     */
    public String getDisplayTime() {
        Instant timestamp = updatedAt != null ? updatedAt : createdAt;
        return timestamp != null ? FORMATTER.format(timestamp) : "未知";
    }
}
