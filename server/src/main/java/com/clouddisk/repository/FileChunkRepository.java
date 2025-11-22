package com.clouddisk.repository;

import com.clouddisk.entity.FileChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileChunkRepository extends JpaRepository<FileChunk, Long> {

    /**
     * 根据哈希值查找块(用于去重)。
     */
    Optional<FileChunk> findByChunkHash(String chunkHash);

    /**
     * 根据存储键查找块。
     */
    Optional<FileChunk> findByStorageKey(String storageKey);
}
