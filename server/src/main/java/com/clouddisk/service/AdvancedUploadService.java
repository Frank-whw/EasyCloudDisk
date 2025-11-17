package com.clouddisk.service;

import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.dto.UploadSessionDto;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.entity.UploadSession;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.FileRepository;
import com.clouddisk.repository.UploadSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 高级上传服务：秒传、断点续传
 */
@Slf4j
@Service
public class AdvancedUploadService {
    
    private final FileRepository fileRepository;
    private final UploadSessionRepository sessionRepository;
    private final FileService fileService;
    private final ChunkService chunkService;
    
    private static final int CHUNK_SIZE = 2 * 1024 * 1024; // 2MB per chunk for resumable upload
    
    public AdvancedUploadService(
            FileRepository fileRepository,
            UploadSessionRepository sessionRepository,
            FileService fileService,
            ChunkService chunkService) {
        this.fileRepository = fileRepository;
        this.sessionRepository = sessionRepository;
        this.fileService = fileService;
        this.chunkService = chunkService;
    }
    
    /**
     * 检查文件是否可以秒传
     * @param hash 文件SHA-256哈希
     * @param userId 用户ID
     * @return 如果存在相同哈希的文件则返回true
     */
    public boolean checkQuickUpload(String hash, String userId) {
        return fileRepository.findAll().stream()
                .anyMatch(file -> hash.equals(file.getContentHash()) && !file.isDirectory());
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
        // 查找具有相同哈希的文件
        FileEntity sourceFile = fileRepository.findAll().stream()
                .filter(file -> hash.equals(file.getContentHash()) && !file.isDirectory())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "未找到可秒传的文件"));
        
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
        newFile.setStorageKey(sourceFile.getStorageKey()); // 复用存储key
        newFile.setFileSize(sourceFile.getFileSize());
        newFile.setContentHash(sourceFile.getContentHash());
        newFile.setVersion(1);
        fileRepository.save(newFile);
        
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
            // TODO: 保存到临时位置，使用sessionId和chunkIndex作为key
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
     * 完成断点续传，合并所有分块
     */
    @Transactional
    public FileMetadataDto completeResumableUpload(String sessionId, String userId) {
        UploadSession session = sessionRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "会话不存在或已过期"));
        
        if (session.getUploadedChunks().size() != session.getTotalChunks()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, 
                    String.format("分块未完整上传: %d/%d", session.getUploadedChunks().size(), session.getTotalChunks()));
        }
        
        // TODO: 合并所有分块
        // 这里简化处理，实际应该从临时存储读取所有分块并合并
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // ... 合并逻辑 ...
        
        session.setStatus("COMPLETED");
        sessionRepository.save(session);
        
        log.info("断点续传完成: sessionId={}, fileName={}", sessionId, session.getFileName());
        
        // 使用标准上传流程保存文件
        // 这里需要将合并后的数据包装成MultipartFile
        // 暂时返回一个占位符
        FileEntity file = new FileEntity();
        file.setUserId(userId);
        file.setName(session.getFileName());
        file.setDirectoryPath(session.getFilePath());
        file.setFileSize(session.getFileSize());
        file.setVersion(1);
        fileRepository.save(file);
        
        return toDto(file);
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
            sessionRepository.deleteAll(expired);
            // TODO: 清理临时文件
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
