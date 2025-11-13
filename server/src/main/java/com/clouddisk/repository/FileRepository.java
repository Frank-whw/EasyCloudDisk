package com.clouddisk.repository;

import com.clouddisk.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文件实体的 JPA 仓储接口。
 */
public interface FileRepository extends JpaRepository<FileEntity, String> {
    List<FileEntity> findAllByUser_UserId(UUID userId);

    Optional<FileEntity> findByFileIdAndUser_UserId(String fileId, UUID userId);

    Optional<FileEntity> findByUser_UserIdAndDirectoryPathAndName(UUID userId, String directoryPath, String name);

    Optional<FileEntity> findFirstByContentHash(String hash);
}
