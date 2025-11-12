package com.clouddisk.repository;

import com.clouddisk.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileEntity, String> {
    List<FileEntity> findAllByUser_UserId(String userId);

    Optional<FileEntity> findByFileIdAndUser_UserId(String fileId, String userId);

    Optional<FileEntity> findByUser_UserIdAndDirectoryPathAndName(String userId, String directoryPath, String name);

    Optional<FileEntity> findFirstByContentHash(String hash);
}
