package com.clouddisk.repository;

import com.clouddisk.entity.FileEncryptionMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileEncryptionMetadataRepository extends JpaRepository<FileEncryptionMetadata, Long> {
    
    Optional<FileEncryptionMetadata> findByFileId(String fileId);
    
    void deleteByFileId(String fileId);
}
