package com.clouddisk.client.sync;

import com.clouddisk.client.model.FileMetadata;
import java.nio.file.Path;

public class ConflictResolver {
    
    /**
     * 解决文件冲突
     * @param local 本地文件路径
     * @param remote 远程文件元数据
     * @return 解决冲突后的文件路径
     */
    public Path resolve(Path local, FileMetadata remote) {
        // TODO: 实现冲突解决逻辑
        return null;
    }
}