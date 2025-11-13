package com.clouddisk.repository;

import com.clouddisk.entity.FileEntity;
import com.clouddisk.entity.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 文件实体的 JPA 仓储接口。
 */
public interface FileRepository extends JpaRepository<FileEntity, String> {
    List<FileEntity> findAllByUserId(String userId);

    Optional<FileEntity> findByFileIdAndUserId(String fileId, String userId);

    Optional<FileEntity> findByUserIdAndDirectoryPathAndName(String userId, String directoryPath, String name);

    Optional<FileEntity> findFirstByContentHash(String hash);
    
    List<FileEntity> findAllByOrderByCreatedAtDesc();
}