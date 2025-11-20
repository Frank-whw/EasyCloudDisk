package com.clouddisk.service;

import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.entity.FileChunk;
import com.clouddisk.entity.FileChunkMapping;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.FileChunkMappingRepository;
import com.clouddisk.repository.FileChunkRepository;
import com.clouddisk.repository.FileRepository;
import com.clouddisk.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * 差分同步服务：使用滚动哈希算法实现增量同步
 */
@Slf4j
@Service
public class DiffSyncService {
    
    private final FileRepository fileRepository;
    private final FileChunkRepository chunkRepository;
    private final FileChunkMappingRepository mappingRepository;
    private final ChunkService chunkService;
    private final StorageService storageService;
    
    private static final int ROLLING_WINDOW_SIZE = 4096; // 4KB滚动窗口
    
    public DiffSyncService(
            FileRepository fileRepository,
            FileChunkRepository chunkRepository,
            FileChunkMappingRepository mappingRepository,
            ChunkService chunkService,
            StorageService storageService) {
        this.fileRepository = fileRepository;
        this.chunkRepository = chunkRepository;
        this.mappingRepository = mappingRepository;
        this.chunkService = chunkService;
        this.storageService = storageService;
    }
    
    /**
     * 获取文件的块签名列表（用于客户端计算差异）
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFileSignatures(String fileId, String userId) {
        FileEntity file = fileRepository.findByFileIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        
        if (!"chunked".equals(file.getStorageKey())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件不支持差分同步");
        }
        
        List<FileChunkMapping> mappings = mappingRepository
                .findByFileIdAndVersionNumberOrderBySequenceNumber(fileId, file.getVersion());
        
        List<Map<String, Object>> signatures = new ArrayList<>();
        for (FileChunkMapping mapping : mappings) {
            FileChunk chunk = chunkRepository.findById(mapping.getChunkId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.STORAGE_ERROR, "块不存在"));
            
            Map<String, Object> sig = new HashMap<>();
            sig.put("chunkIndex", mapping.getSequenceNumber());
            sig.put("hash", chunk.getChunkHash());
            sig.put("size", chunk.getChunkSize());
            sig.put("offset", mapping.getOffsetInFile());
            signatures.add(sig);
        }
        
        return signatures;
    }
    
    /**
     * 应用差分更新
     * @param fileId 文件ID
     * @param userId 用户ID
     * @param deltaChunks 变更的块数据（索引 -> 数据）
     * @param chunkHashes 所有块的哈希信息（索引 -> 哈希），用于匹配和复用
     * @return 更新后的文件元数据
     */
    @Transactional
    public FileMetadataDto applyDelta(String fileId, String userId, 
                                      Map<Integer, byte[]> deltaChunks,
                                      Map<Integer, String> chunkHashes) {
        // 注意：由于 ChunkService.storeFileInChunks 使用 REQUIRES_NEW，
        // 我们需要确保删除操作在当前事务中完成，这样新事务才能看到删除后的状态
        FileEntity file = fileRepository.findByFileIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        
        if (!"chunked".equals(file.getStorageKey())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件不支持差分同步");
        }
        
        // 获取现有块映射（用于查找可复用的块）
        Integer oldVersion = file.getVersion();
        List<FileChunkMapping> oldMappings = mappingRepository
                .findByFileIdAndVersionNumberOrderBySequenceNumber(fileId, oldVersion);
        
        // 构建哈希到块的映射（用于快速查找可复用的块）
        Map<String, FileChunk> hashToChunkMap = new HashMap<>();
        for (FileChunkMapping mapping : oldMappings) {
            FileChunk chunk = chunkRepository.findById(mapping.getChunkId())
                    .orElse(null);
            if (chunk != null) {
                hashToChunkMap.put(chunk.getChunkHash(), chunk);
            }
        }
        
        // 先删除旧版本的映射（但保留块，因为可能被其他版本引用）
        // 使用 deleteByFileIdAndVersionNumber 确保删除正确执行
        // 必须在更新版本号之前删除，避免唯一索引冲突
        mappingRepository.deleteByFileIdAndVersionNumber(fileId, oldVersion);
        
        // 更新版本号（在删除之后更新，这样新映射会使用新版本号）
        file.setVersion(file.getVersion() + 1);
        fileRepository.save(file);
        
        // 根据新文件的块信息重新组装文件
        ByteArrayOutputStream newFileData = new ByteArrayOutputStream();
        long totalSize = 0;
        
        // 获取新文件的总块数
        int maxChunkIndex = chunkHashes.keySet().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(-1);
        
        if (maxChunkIndex < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "新文件块信息为空");
        }
        
        // 按索引顺序处理每个块
        for (int chunkIndex = 0; chunkIndex <= maxChunkIndex; chunkIndex++) {
            byte[] chunkData;
            String chunkHash = chunkHashes.get(chunkIndex);
            
            if (chunkHash == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, 
                    "缺少块索引 " + chunkIndex + " 的哈希信息");
            }
            
            if (deltaChunks.containsKey(chunkIndex)) {
                // 使用新上传的块数据
                chunkData = deltaChunks.get(chunkIndex);
                log.info("应用差异块: fileId={}, chunkIndex={}, size={}, hash={}", 
                        fileId, chunkIndex, chunkData.length, chunkHash);
            } else {
                // 尝试复用旧块（通过哈希匹配）
                FileChunk existingChunk = hashToChunkMap.get(chunkHash);
                if (existingChunk != null) {
                    // 找到匹配的块，复用
                    try {
                        chunkData = storageService.loadFile(
                                existingChunk.getStorageKey(), 
                                existingChunk.getCompressed())
                                .readAllBytes();
                        log.info("复用块: fileId={}, chunkIndex={}, hash={}", 
                                fileId, chunkIndex, chunkHash);
                    } catch (IOException e) {
                        throw new BusinessException(ErrorCode.STORAGE_ERROR, 
                            "读取块数据失败: " + e.getMessage(), e);
                    }
                } else {
                    // 块不存在且没有新数据，这是错误情况
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, 
                        "块索引 " + chunkIndex + " 既没有新数据，也无法从服务器匹配");
                }
            }
            
            try {
                newFileData.write(chunkData);
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "写入数据失败", e);
            }
            
            totalSize += chunkData.length;
        }
        
        // 计算新文件哈希
        byte[] finalData = newFileData.toByteArray();
        String newHash = DigestUtils.sha256Hex(finalData);
        
        file.setContentHash(newHash);
        file.setFileSize(totalSize);
        fileRepository.save(file);
        
        // 重新存储为块（利用去重）
        chunkService.storeFileInChunks(fileId, file.getVersion(), finalData, userId, true);
        
        log.info("差分同步完成: fileId={}, newVersion={}, deltaCount={}, totalChunks={}, totalSize={}", 
                fileId, file.getVersion(), deltaChunks.size(), chunkHashes.size(), totalSize);
        
        return toDto(file);
    }
    
    /**
     * 计算滚动哈希（Rabin-Karp算法的简化版本）
     */
    public int rollingHash(byte[] data, int offset, int length) {
        int hash = 0;
        int prime = 31;
        
        for (int i = 0; i < length && (offset + i) < data.length; i++) {
            hash = hash * prime + (data[offset + i] & 0xFF);
        }
        
        return hash;
    }
    
    /**
     * 查找文件中的匹配块（用于客户端SDK）
     */
    public List<Integer> findMatchingBlocks(byte[] newData, List<Map<String, Object>> serverSignatures) {
        List<Integer> matchedIndices = new ArrayList<>();
        Set<String> serverHashes = new HashSet<>();
        
        for (Map<String, Object> sig : serverSignatures) {
            serverHashes.add((String) sig.get("hash"));
        }
        
        // 按块大小分割新数据并计算哈希
        int chunkSize = ChunkService.CHUNK_SIZE;
        for (int i = 0; i < newData.length; i += chunkSize) {
            int end = Math.min(i + chunkSize, newData.length);
            byte[] chunk = Arrays.copyOfRange(newData, i, end);
            String hash = DigestUtils.sha256Hex(chunk);
            
            if (serverHashes.contains(hash)) {
                matchedIndices.add(i / chunkSize);
            }
        }
        
        return matchedIndices;
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
