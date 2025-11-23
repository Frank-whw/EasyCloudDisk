package com.clouddisk.service;

import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.dto.FileVersionDto;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.entity.FileVersion;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.FileRepository;
import com.clouddisk.repository.FileVersionRepository;
import com.clouddisk.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件版本管理服务
 */
@Slf4j
@Service
public class FileVersionService {
    
    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final StorageService storageService;
    private final ChunkService chunkService;
    private final FileSyncService fileSyncService;
    
    public FileVersionService(FileRepository fileRepository,
                            FileVersionRepository fileVersionRepository,
                            StorageService storageService,
                            ChunkService chunkService,
                            FileSyncService fileSyncService) {
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.storageService = storageService;
        this.chunkService = chunkService;
        this.fileSyncService = fileSyncService;
    }
    
    /**
     * 获取文件版本历史
     */
    @Transactional(readOnly = true)
    public List<FileVersionDto> getVersionHistory(String fileId, String userId) {
        // 验证用户权限
        FileEntity file = fileRepository.findByFileIdAndUserId(fileId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        
        List<FileVersion> versions = fileVersionRepository.findAllByFileIdOrderByVersionNumberDesc(fileId);
        
        log.info("获取文件版本历史: fileId={}, userId={}, 找到版本数量={}", fileId, userId, versions.size());
        
        return versions.stream()
            .map(this::toVersionDto)
            .collect(Collectors.toList());
    }
    
    /**
     * 下载指定版本的文件
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadVersion(String fileId, Integer versionNumber, String userId) {
        // 验证用户权限
        FileEntity file = fileRepository.findByFileIdAndUserId(fileId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        
        if (file.isDirectory()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "目录无法下载");
        }
        
        // 查找指定版本
        FileVersion version = fileVersionRepository.findAllByFileIdOrderByVersionNumberDesc(fileId)
            .stream()
            .filter(v -> v.getVersionNumber() == versionNumber)
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "版本不存在"));
        
        InputStream stream;
        if ("chunked".equals(version.getStorageKey())) {
            // 从块重组文件
            try {
                stream = chunkService.assembleFile(fileId, versionNumber);
            } catch (BusinessException e) {
                // 如果块映射不存在，可能是数据不一致，尝试从OSS直接下载
                if (e.getMessage().contains("文件块映射不存在")) {
                    log.warn("版本文件 {} 标记为chunked但块映射不存在，尝试从OSS下载", fileId);
                    // 如果有storageKey但不是chunked，尝试直接下载
                    if (version.getStorageKey() != null && !"chunked".equals(version.getStorageKey())) {
                        stream = storageService.loadFile(version.getStorageKey(), true);
                    } else {
                        // 如果也没有storageKey，抛出错误
                        throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "版本文件数据不存在");
                    }
                } else {
                    throw e;
                }
            }
        } else {
            // 直接从存储加载
            stream = storageService.loadFile(version.getStorageKey(), true);
        }
        
        Resource resource = new InputStreamResource(stream);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
            .filename(file.getName() + "_v" + versionNumber, StandardCharsets.UTF_8)
            .build();
        
        return ResponseEntity.ok()
            .headers(headers -> headers.setContentDisposition(contentDisposition))
            .contentType(getContentTypeForFile(file.getName()))
            .body(resource);
    }
    
    /**
     * 恢复到指定版本
     */
    @Transactional
    public FileMetadataDto restoreVersion(String fileId, Integer versionNumber, String userId) {
        // 验证用户权限
        FileEntity file = fileRepository.findByFileIdAndUserId(fileId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        
        if (file.isDirectory()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "目录无法恢复版本");
        }
        
        // 查找指定版本
        FileVersion targetVersion = fileVersionRepository.findAllByFileIdOrderByVersionNumberDesc(fileId)
            .stream()
            .filter(v -> v.getVersionNumber() == versionNumber)
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "版本不存在"));
        
        // 保存当前版本到历史
        saveCurrentVersionToHistory(file);
        
        // 恢复到目标版本
        file.setStorageKey(targetVersion.getStorageKey());
        file.setFileSize(targetVersion.getFileSize());
        file.setContentHash(targetVersion.getContentHash());
        file.setVersion(file.getVersion() + 1); // 递增版本号
        file.setUpdatedAt(Instant.now());
        
        fileRepository.save(file);
        
        // 保存新的版本记录
        FileVersion newVersion = new FileVersion();
        newVersion.setFileId(file.getFileId());
        newVersion.setVersionNumber(file.getVersion());
        newVersion.setStorageKey(file.getStorageKey());
        newVersion.setFileSize(file.getFileSize());
        newVersion.setContentHash(file.getContentHash());
        newVersion.setCreatedAt(Instant.now());
        fileVersionRepository.save(newVersion);
        
        // 通知其他用户
        fileSyncService.notifyVersionRestore(fileId, userId, versionNumber, file.getVersion());
        
        log.info("文件版本恢复成功: fileId={}, fromVersion={}, toVersion={}, userId={}", 
                fileId, versionNumber, file.getVersion(), userId);
        
        return toDto(file);
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
     * 转换为版本 DTO
     */
    private FileVersionDto toVersionDto(FileVersion version) {
        FileVersionDto dto = new FileVersionDto();
        dto.setVersionNumber(version.getVersionNumber());
        dto.setFileSize(version.getFileSize());
        dto.setContentHash(version.getContentHash());
        dto.setCreatedAt(version.getCreatedAt());
        return dto;
    }
    
    /**
     * 转换为文件 DTO
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

    /**
     * 根据文件扩展名获取合适的Content-Type
     */
    private MediaType getContentTypeForFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        String extension = fileName.toLowerCase();
        if (extension.endsWith(".txt") || extension.endsWith(".md") ||
            extension.endsWith(".log") || extension.endsWith(".csv")) {
            return MediaType.TEXT_PLAIN;
        } else if (extension.endsWith(".html") || extension.endsWith(".htm")) {
            return MediaType.TEXT_HTML;
        } else if (extension.endsWith(".css")) {
            return MediaType.valueOf("text/css");
        } else if (extension.endsWith(".js")) {
            return MediaType.valueOf("application/javascript");
        } else if (extension.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        } else if (extension.endsWith(".xml")) {
            return MediaType.APPLICATION_XML;
        } else if (extension.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        } else if (extension.endsWith(".jpg") || extension.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        } else if (extension.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else if (extension.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        } else if (extension.endsWith(".zip")) {
            return MediaType.valueOf("application/zip");
        } else if (extension.endsWith(".rar")) {
            return MediaType.valueOf("application/x-rar-compressed");
        } else {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
