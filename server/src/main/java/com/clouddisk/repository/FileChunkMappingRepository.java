package com.clouddisk.repository;

import com.clouddisk.entity.FileChunkMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileChunkMappingRepository extends JpaRepository<FileChunkMapping, Long> {

    /**
     * 查找文件的所有块映射,按序号排序。
     */
    List<FileChunkMapping> findByFileIdAndVersionNumberOrderBySequenceNumber(String fileId, Integer versionNumber);

    /**
     * 查找文件的所有块映射。
     */
    List<FileChunkMapping> findByFileId(String fileId);

    /**
     * 删除文件的所有块映射。
     */
    void deleteByFileId(String fileId);

    /**
     * 删除指定版本的块映射。
     */
    void deleteByFileIdAndVersionNumber(String fileId, Integer versionNumber);
}
