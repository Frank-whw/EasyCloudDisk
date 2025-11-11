package com.clouddisk.file.repository;

import com.clouddisk.file.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<File, UUID> {
    
    /**
     * 根据用户ID查询所有文件
     */
    List<File> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    /**
     * 根据用户ID和文件路径查询文件
     */
    Optional<File> findByUserIdAndFilePathAndName(UUID userId, String filePath, String name);
    
    /**
     * 根据用户ID和S3 Key查询文件
     */
    Optional<File> findByUserIdAndS3Key(UUID userId, String s3Key);
    
    /**
     * 根据用户ID和内容哈希查询文件（用于检测重复文件）
     */
    Optional<File> findByUserIdAndContentHash(UUID userId, String contentHash);
    
    /**
     * 统计用户的文件数量
     */
    long countByUserId(UUID userId);
    
    /**
     * 统计用户的总文件大小
     */
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM File f WHERE f.userId = :userId")
    long getTotalFileSizeByUserId(@Param("userId") UUID userId);
    
    /**
     * 根据文件路径前缀查询文件（用于文件夹浏览）
     */
    List<File> findByUserIdAndFilePathStartingWithOrderByFilePathAscNameAsc(
            UUID userId, String filePathPrefix);
    
    /**
     * 统计指定 S3 key 的文件引用数量（用于删除时检查）
     */
    long countByS3Key(String s3Key);
    
    /**
     * 根据 contentHash 查找所有文件（跨用户，用于全局去重）
     */
    Optional<File> findFirstByContentHash(String contentHash);

    /**
     * 根据文件ID与用户ID查询文件（用于权限校验与下载/信息获取）
     */
    Optional<File> findByFileIdAndUserId(UUID fileId, UUID userId);
}