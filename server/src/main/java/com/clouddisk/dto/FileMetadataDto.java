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
    public void setShared(boolean b) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setShared'");
    }
    public void setPermission(String name2) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setPermission'");
    }
    public void setOwnerEmail(String string) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setOwnerEmail'");
    }
    public void setShareId(String shareId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setShareId'");
    }

}
