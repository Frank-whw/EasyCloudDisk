package com.clouddisk.repository;

import com.clouddisk.entity.FileShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileShareRepository extends JpaRepository<FileShare, String> {

    /**
     * 根据共享ID查找有效的共享
     */
    @Query("SELECT fs FROM FileShare fs WHERE fs.shareId = :shareId AND fs.active = true " +
           "AND (fs.expiresAt IS NULL OR fs.expiresAt > :now) " +
           "AND (fs.maxDownloads IS NULL OR fs.downloadCount < fs.maxDownloads)")
    Optional<FileShare> findActiveShare(@Param("shareId") String shareId, @Param("now") Instant now);

    /**
     * 根据文件ID查找所有共享
     */
    List<FileShare> findAllByFileIdAndActiveTrue(String fileId);

    /**
     * 根据所有者ID查找所有共享
     */
    List<FileShare> findAllByOwnerIdAndActiveTrue(String ownerId);

    /**
     * 根据文件ID和所有者ID查找共享
     */
    Optional<FileShare> findByFileIdAndOwnerIdAndActiveTrue(String fileId, String ownerId);

    /**
     * 查找过期的共享
     */
    @Query("SELECT fs FROM FileShare fs WHERE fs.active = true AND fs.expiresAt IS NOT NULL AND fs.expiresAt <= :now")
    List<FileShare> findExpiredShares(@Param("now") Instant now);

    /**
     * 查找达到最大下载次数的共享
     */
    @Query("SELECT fs FROM FileShare fs WHERE fs.active = true AND fs.maxDownloads IS NOT NULL AND fs.downloadCount >= fs.maxDownloads")
    List<FileShare> findMaxDownloadReachedShares();
}
