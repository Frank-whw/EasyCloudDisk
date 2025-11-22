package com.clouddisk.service;

import com.clouddisk.dto.ConflictResolutionRequest;
import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.entity.FileVersion;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ConflictException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.FileRepository;
import com.clouddisk.repository.FileVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Optional;

/**
 * 冲突解决服务
 */
@Slf4j
@Service
public class ConflictResolutionService {
    
    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final FileService fileService;
    private final FileSyncService fileSyncService;
    
    public ConflictResolutionService(FileRepository fileRepository,
                                   FileVersionRepository fileVersionRepository,
                                   FileService fileService,
                                   FileSyncService fileSyncService) {
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.fileService = fileService;
        this.fileSyncService = fileSyncService;
    }
    
    /**
     * 检查文件上传是否存在冲突
     */
    public void checkForConflicts(String fileId, Long expectedVersion, String userId) {
        Optional<FileEntity> fileOpt = fileRepository.findByFileIdAndUserId(fileId, userId);
        if (fileOpt.isEmpty()) {
            return; // 新文件，无冲突
        }
        
        FileEntity file = fileOpt.get();
        if (expectedVersion != null && !expectedVersion.equals(file.getOptimisticLockVersion())) {
            // 发送冲突通知
            fileSyncService.notifyConflict(fileId, userId, java.util.Map.of(
                "conflictType", "VERSION_CONFLICT",
                "expectedVersion", expectedVersion,
                "currentVersion", file.getOptimisticLockVersion(),
                "fileName", file.getName()
            ));
            
            throw new ConflictException(
                "文件已被其他用户修改，请刷新后重试",
                "VERSION_CONFLICT",
                String.format("期望版本: %d, 当前版本: %d", expectedVersion, file.getOptimisticLockVersion())
            );
        }
    }
    
    /**
     * 解决文件冲突
     */
    @Transactional
    public FileMetadataDto resolveConflict(String fileId, String userId, 
                                          MultipartFile newFile, 
                                          ConflictResolutionRequest request) {
        FileEntity existingFile = fileRepository.findByFileIdAndUserId(fileId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        
        switch (request.getStrategy()) {
            case "OVERWRITE":
                return handleOverwrite(existingFile, newFile, userId, request.getExpectedVersion());
            case "CREATE_COPY":
                return handleCreateCopy(existingFile, newFile, userId, request.getNewFileName());
            case "MERGE":
                return handleMerge(existingFile, request.getMergedContent(), userId);
            default:
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的冲突解决策略: " + request.getStrategy());
        }
    }
    
    /**
     * 强制覆盖策略
     */
    private FileMetadataDto handleOverwrite(FileEntity existingFile, MultipartFile newFile, 
                                          String userId, Long expectedVersion) {
        try {
            // 验证版本号
            if (expectedVersion != null && !expectedVersion.equals(existingFile.getOptimisticLockVersion())) {
                throw new ConflictException(
                    "版本不匹配，无法覆盖",
                    "VERSION_MISMATCH",
                    "请刷新后重试"
                );
            }
            
            // 保存当前版本到历史
            saveCurrentVersionToHistory(existingFile);
            
            // 执行覆盖上传
            FileMetadataDto result = fileService.upload(newFile, existingFile.getDirectoryPath(), userId);
            
            // 通知其他用户
            fileSyncService.notifyChange(userId, java.util.Map.of(
                "type", "conflict_resolved",
                "fileId", existingFile.getFileId(),
                "strategy", "OVERWRITE"
            ));
            
            log.info("文件冲突已通过覆盖解决: fileId={}, userId={}", existingFile.getFileId(), userId);
            return result;
            
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException(
                "文件正在被其他用户修改，请稍后重试",
                "CONCURRENT_MODIFICATION",
                "乐观锁冲突"
            );
        }
    }
    
    /**
     * 创建副本策略
     */
    private FileMetadataDto handleCreateCopy(FileEntity existingFile, MultipartFile newFile, 
                                           String userId, String newFileName) {
        if (newFileName == null || newFileName.trim().isEmpty()) {
            // 自动生成冲突副本名称
            String originalName = existingFile.getName();
            String timestamp = String.valueOf(System.currentTimeMillis());
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex > 0) {
                newFileName = originalName.substring(0, dotIndex) + "_conflict_" + timestamp + originalName.substring(dotIndex);
            } else {
                newFileName = originalName + "_conflict_" + timestamp;
            }
        }
        
        // 创建新文件
        FileMetadataDto result = fileService.upload(newFile, existingFile.getDirectoryPath(), userId);
        
        // 重命名为冲突副本名称
        FileEntity newFileEntity = fileRepository.findById(result.getFileId())
            .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        newFileEntity.setName(newFileName);
        fileRepository.save(newFileEntity);
        result.setName(newFileName);
        
        // 通知其他用户
        fileSyncService.notifyChange(userId, java.util.Map.of(
            "type", "conflict_resolved",
            "fileId", existingFile.getFileId(),
            "strategy", "CREATE_COPY",
            "newFileId", result.getFileId(),
            "newFileName", newFileName
        ));
        
        log.info("文件冲突已通过创建副本解决: originalFileId={}, newFileId={}, newFileName={}", 
                existingFile.getFileId(), result.getFileId(), newFileName);
        return result;
    }
    
    /**
     * 合并策略（适用于文本文件）
     */
    private FileMetadataDto handleMerge(FileEntity existingFile, String mergedContent, String userId) {
        if (mergedContent == null || mergedContent.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "合并内容不能为空");
        }
        
        try {
            // 保存当前版本到历史
            saveCurrentVersionToHistory(existingFile);
            
            // 创建临时文件用于上传合并内容
            // 这里简化处理，实际应该创建 MultipartFile 对象
            // TODO: 实现文本内容到 MultipartFile 的转换
            
            // 更新文件元数据
            existingFile.setVersion(existingFile.getVersion() + 1);
            existingFile.setUpdatedAt(Instant.now());
            fileRepository.save(existingFile);
            
            // 通知其他用户
            fileSyncService.notifyChange(userId, java.util.Map.of(
                "type", "conflict_resolved",
                "fileId", existingFile.getFileId(),
                "strategy", "MERGE"
            ));
            
            log.info("文件冲突已通过合并解决: fileId={}, userId={}", existingFile.getFileId(), userId);
            
            return toDto(existingFile);
            
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException(
                "合并过程中文件被其他用户修改",
                "CONCURRENT_MODIFICATION",
                "请重新获取最新版本后合并"
            );
        }
    }
    
    /**
     * 保存当前版本到历史记录
     */
    private void saveCurrentVersionToHistory(FileEntity file) {
        FileVersion version = new FileVersion();
        version.setFileId(file.getFileId());
        version.setVersionNumber(file.getVersion());
        version.setStorageKey(file.getStorageKey());
        version.setFileSize(file.getFileSize());
        version.setContentHash(file.getContentHash());
        version.setCreatedAt(file.getUpdatedAt());
        fileVersionRepository.save(version);
    }
    
    /**
     * 转换为 DTO
     */
    private FileMetadataDto toDto(FileEntity entity) {
        FileMetadataDto dto = new FileMetadataDto();
        dto.setFileId(entity.getFileId());
        dto.setName(entity.getName());
        dto.setPath(entity.getDirectoryPath());
        dto.setSize(entity.getFileSize());
        dto.setDirectory(entity.isDirectory());
        dto.setHash(entity.getContentHash());
        dto.setVersion(entity.getVersion());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
