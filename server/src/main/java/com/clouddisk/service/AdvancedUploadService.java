package com.clouddisk.service;

import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.dto.UploadSessionDto;
import com.clouddisk.entity.FileChunkMapping;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.entity.FileVersion;
import com.clouddisk.entity.UploadSession;
import com.clouddisk.repository.FileChunkMappingRepository;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.FileRepository;
import com.clouddisk.repository.FileVersionRepository;
import com.clouddisk.repository.UploadSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 高级上传服务：秒传、断点续传
 */
@Slf4j
@Service
public class AdvancedUploadService {
    
    private final FileRepository fileRepository;
    private final UploadSessionRepository sessionRepository;
    private final FileVersionRepository fileVersionRepository;
    private final FileChunkMappingRepository mappingRepository;
    private final FileService fileService;
    private final ChunkService chunkService;
    
    private static final int CHUNK_SIZE = 2 * 1024 * 1024; // 2MB per chunk for resumable upload
    
    // 临时存储分块数据 (sessionId -> (chunkIndex -> chunkData))
    private final Map<String, Map<Integer, byte[]>> chunkStorage = new ConcurrentHashMap<>();
    
    public AdvancedUploadService(
            FileRepository fileRepository,
            UploadSessionRepository sessionRepository,
            FileVersionRepository fileVersionRepository,
            FileChunkMappingRepository mappingRepository,
            FileService fileService,
            ChunkService chunkService) {
        this.fileRepository = fileRepository;
        this.sessionRepository = sessionRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.mappingRepository = mappingRepository;
        this.fileService = fileService;
        this.chunkService = chunkService;
    }
    
    /**
     * 检查文件是否可以秒传
     * @param hash 文件SHA-256哈希
     * @param userId 用户ID
     * @return 如果存在相同哈希的文件或文件版本则返回true
     */
    public boolean checkQuickUpload(String hash, String userId) {
        // 检查当前文件表中是否有相同哈希的文件
        boolean existsInFiles = fileRepository.findAll().stream()
                .anyMatch(file -> hash.equals(file.getContentHash()) && !file.isDirectory());
        
        if (existsInFiles) {
            return true;
        }
        
        // 检查文件版本表中是否有相同哈希的版本（即使文件被删除，版本记录可能还在）
        // 注意：如果版本记录也被删除，则无法秒传，但块级去重仍然有效
        boolean existsInVersions = fileVersionRepository.findAll().stream()
                .anyMatch(version -> hash.equals(version.getContentHash()) && "chunked".equals(version.getStorageKey()));
        
        return existsInVersions;
    }
    
    /**
     * 执行秒传：复制已存在文件的引用
     * @param hash 文件哈希
     * @param fileName 新文件名
     * @param path 目标路径
     * @param userId 用户ID
     * @return 文件元数据
     */
    @Transactional
    public FileMetadataDto quickUpload(String hash, String fileName, String path, String userId) {
        // 首先查找当前文件表中是否有相同哈希的文件
        FileEntity sourceFile = fileRepository.findAll().stream()
                .filter(file -> hash.equals(file.getContentHash()) && !file.isDirectory())
                .findFirst()
                .orElse(null);
        
        // 如果当前文件表中没有，尝试从文件版本表中查找
        com.clouddisk.entity.FileVersion sourceVersion = null;
        if (sourceFile == null) {
            sourceVersion = fileVersionRepository.findAll().stream()
                    .filter(version -> hash.equals(version.getContentHash()) && "chunked".equals(version.getStorageKey()))
                    .findFirst()
                    .orElse(null);
        }
        
        if (sourceFile == null && sourceVersion == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "未找到可秒传的文件");
        }
        
        // 检查是否已存在同名文件
        String normalizedPath = path == null || path.isEmpty() ? "/" : path;
        fileRepository.findByUserIdAndDirectoryPathAndName(userId, normalizedPath, fileName)
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件已存在");
                });
        
        // 创建新的文件实体，复用存储
        FileEntity newFile = new FileEntity();
        newFile.setUserId(userId);
        newFile.setDirectory(false);
        newFile.setDirectoryPath(normalizedPath);
        newFile.setName(fileName);
        
        if (sourceFile != null) {
            // 从当前文件复用
            newFile.setStorageKey(sourceFile.getStorageKey());
            newFile.setFileSize(sourceFile.getFileSize());
            newFile.setContentHash(sourceFile.getContentHash());
        } else {
            // 从文件版本复用
            newFile.setStorageKey(sourceVersion.getStorageKey());
            newFile.setFileSize(sourceVersion.getFileSize());
            newFile.setContentHash(sourceVersion.getContentHash());
        }
        
        newFile.setVersion(1);
        fileRepository.save(newFile);
        
        // 如果从版本恢复，需要复制原文件的块映射
        if (sourceVersion != null) {
            // 查找原文件的块映射（通过fileId和versionNumber）
            List<FileChunkMapping> sourceMappings = mappingRepository
                    .findByFileIdAndVersionNumberOrderBySequenceNumber(
                            sourceVersion.getFileId(), sourceVersion.getVersionNumber());
            
            if (!sourceMappings.isEmpty()) {
                // 复制块映射到新文件
                for (FileChunkMapping sourceMapping : sourceMappings) {
                    FileChunkMapping newMapping = new FileChunkMapping();
                    newMapping.setFileId(newFile.getFileId());
                    newMapping.setVersionNumber(newFile.getVersion());
                    newMapping.setChunkId(sourceMapping.getChunkId());
                    newMapping.setSequenceNumber(sourceMapping.getSequenceNumber());
                    newMapping.setOffsetInFile(sourceMapping.getOffsetInFile());
                    mappingRepository.save(newMapping);
                    
                    // 增加块的引用计数
                    // 注意：块映射被删除时引用计数会减少，现在恢复时需要增加
                    // 但由于块级去重会自动处理引用计数，这里不需要手动增加
                }
                log.info("从文件版本恢复秒传: userId={}, hash={}, fileName={}, sourceFileId={}, 复制了{}个块映射", 
                        userId, hash, fileName, sourceVersion.getFileId(), sourceMappings.size());
            } else {
                log.warn("从文件版本恢复秒传，但未找到块映射: fileId={}, version={}", 
                        sourceVersion.getFileId(), sourceVersion.getVersionNumber());
            }
        }
        
        log.info("秒传成功: userId={}, hash={}, fileName={}", userId, hash, fileName);
        
        return toDto(newFile);
    }
    
    /**
     * 初始化断点续传会话
     */
    @Transactional
    public UploadSessionDto initResumableUpload(String fileName, String path, Long fileSize, String userId) {
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        
        UploadSession session = new UploadSession();
        session.setUserId(userId);
        session.setFileName(fileName);
        session.setFilePath(path == null || path.isEmpty() ? "/" : path);
        session.setFileSize(fileSize);
        session.setTotalChunks(totalChunks);
        session.setChunkSize(CHUNK_SIZE);
        session.setStatus("ACTIVE");
        sessionRepository.save(session);
        
        log.info("创建断点续传会话: sessionId={}, fileName={}, totalChunks={}", 
                session.getSessionId(), fileName, totalChunks);
        
        return toSessionDto(session);
    }
    
    /**
     * 上传单个分块
     */
    @Transactional
    public UploadSessionDto uploadChunk(String sessionId, Integer chunkIndex, MultipartFile chunk, String userId) {
        UploadSession session = sessionRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "会话不存在或已过期"));
        
        if (!"ACTIVE".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "会话已完成或已过期");
        }
        
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无效的分块索引");
        }
        
        // 保存分块数据到临时存储
        try {
            byte[] data = chunk.getBytes();
            chunkStorage.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(chunkIndex, data);
            log.debug("保存分块: sessionId={}, chunkIndex={}, size={}", sessionId, chunkIndex, data.length);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取分块数据失败", e);
        }
        
        session.getUploadedChunks().add(chunkIndex);
        sessionRepository.save(session);
        
        log.info("上传分块成功: sessionId={}, chunkIndex={}, progress={}/{}", 
                sessionId, chunkIndex, session.getUploadedChunks().size(), session.getTotalChunks());
        
        return toSessionDto(session);
    }
    
    /**
     * 完成断点续传，合并所有分块并保存（使用块级去重和压缩）
     */
    @Transactional
    public FileMetadataDto completeResumableUpload(String sessionId, String userId) {
        UploadSession session = sessionRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "会话不存在或已过期"));
        
        if (session.getUploadedChunks().size() != session.getTotalChunks()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, 
                    String.format("分块未完整上传: %d/%d", session.getUploadedChunks().size(), session.getTotalChunks()));
        }
        
        // 从临时存储读取所有分块并合并
        Map<Integer, byte[]> chunks = chunkStorage.get(sessionId);
        if (chunks == null || chunks.size() != session.getTotalChunks()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "分块数据不存在或不完整");
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            // 按顺序合并所有分块
            for (int i = 0; i < session.getTotalChunks(); i++) {
                byte[] chunkData = chunks.get(i);
                if (chunkData == null) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, 
                            String.format("分块 %d 不存在", i));
                }
                outputStream.write(chunkData);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "合并分块失败", e);
        }
        
        byte[] fileData = outputStream.toByteArray();
        
        // 计算文件哈希
        String hash = DigestUtils.sha256Hex(fileData);
        session.setFileHash(hash);
        
        // 清理临时存储
        chunkStorage.remove(sessionId);
        
        // 标准化路径
        String normalizedPath = session.getFilePath() == null || session.getFilePath().isEmpty() 
                ? "/" : session.getFilePath();
        
        // 检查是否已存在同名文件
        FileEntity entity = fileRepository.findByUserIdAndDirectoryPathAndName(
                userId, normalizedPath, session.getFileName()).orElse(null);
        
        boolean isNewFile = (entity == null);
        if (isNewFile) {
            entity = new FileEntity();
            entity.setUserId(userId);
            entity.setDirectory(false);
            entity.setDirectoryPath(normalizedPath);
            entity.setName(session.getFileName());
            entity.setVersion(1);
        } else {
            // 保存旧版本
            FileVersion version = new FileVersion();
            version.setFileId(entity.getFileId());
            version.setVersionNumber(entity.getVersion());
            version.setStorageKey(entity.getStorageKey());
            version.setFileSize(entity.getFileSize());
            version.setContentHash(entity.getContentHash());
            fileVersionRepository.save(version);
            entity.setVersion(entity.getVersion() + 1);
        }
        
        entity.setStorageKey("chunked"); // 标记为分块存储
        entity.setFileSize((long) fileData.length);
        entity.setContentHash(hash);
        fileRepository.save(entity);
        
        // 使用块级存储(自动去重+压缩)
        chunkService.storeFileInChunks(
                entity.getFileId(), 
                entity.getVersion(), 
                fileData, 
                userId, 
                true  // 启用压缩
        );
        
        // 保存当前版本信息
        FileVersion latest = new FileVersion();
        latest.setFileId(entity.getFileId());
        latest.setVersionNumber(entity.getVersion());
        latest.setStorageKey("chunked");
        latest.setFileSize((long) fileData.length);
        latest.setContentHash(hash);
        fileVersionRepository.save(latest);
        
        session.setStatus("COMPLETED");
        sessionRepository.save(session);
        
        log.info("断点续传完成: sessionId={}, fileName={}, fileId={}, 上传分块数={}, 文件大小={}MB, 文件哈希={}...", 
                sessionId, session.getFileName(), entity.getFileId(), session.getTotalChunks(),
                String.format("%.2f", fileData.length / 1024.0 / 1024.0), hash.substring(0, 8));
        log.info("开始块级去重处理: fileId={}, 文件将被切分为4MB块进行去重和压缩", entity.getFileId());
        
        return toDto(entity);
    }
    
    /**
     * 获取用户的所有上传会话
     */
    @Transactional(readOnly = true)
    public List<UploadSessionDto> listSessions(String userId) {
        return sessionRepository.findAllByUserId(userId).stream()
                .map(this::toSessionDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 定时清理过期会话（每小时执行一次）
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanExpiredSessions() {
        List<UploadSession> expired = sessionRepository.findAllByExpiresAtBefore(LocalDateTime.now());
        if (!expired.isEmpty()) {
            log.info("清理过期会话: count={}", expired.size());
            // 清理临时存储的分块数据
            for (UploadSession session : expired) {
                chunkStorage.remove(session.getSessionId());
            }
            sessionRepository.deleteAll(expired);
        }
    }
    
    private UploadSessionDto toSessionDto(UploadSession session) {
        return new UploadSessionDto(
                session.getSessionId(),
                session.getFileName(),
                session.getFileSize(),
                session.getTotalChunks(),
                session.getUploadedChunks().size(),
                session.getStatus()
        );
    }
    
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
