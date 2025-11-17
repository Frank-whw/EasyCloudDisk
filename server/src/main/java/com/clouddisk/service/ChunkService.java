package com.clouddisk.service;

import com.clouddisk.entity.FileChunk;
import com.clouddisk.entity.FileChunkMapping;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.FileChunkMappingRepository;
import com.clouddisk.repository.FileChunkRepository;
import com.clouddisk.storage.StorageService;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * 块级存储服务,实现文件分块、去重和组装。
 */
@Service
public class ChunkService {

    private static final Logger log = LoggerFactory.getLogger(ChunkService.class);

    /**
     * 块大小: 4MB。
     */
    public static final int CHUNK_SIZE = 4 * 1024 * 1024;

    private final FileChunkRepository chunkRepository;
    private final FileChunkMappingRepository mappingRepository;
    private final StorageService storageService;

    public ChunkService(FileChunkRepository chunkRepository,
                        FileChunkMappingRepository mappingRepository,
                        StorageService storageService) {
        this.chunkRepository = chunkRepository;
        this.mappingRepository = mappingRepository;
        this.storageService = storageService;
    }

    /**
     * 将文件分块并存储,返回块映射信息。
     * 
     * @param fileId 文件ID
     * @param versionNumber 版本号
     * @param fileData 文件数据
     * @param userId 用户ID
     * @param compress 是否压缩
     * @return 存储的块数量
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int storeFileInChunks(String fileId, Integer versionNumber, byte[] fileData, 
                                  String userId, boolean compress) {
        List<byte[]> chunks = splitIntoChunks(fileData);
        log.debug("File {} split into {} chunks", fileId, chunks.size());

        long offset = 0;
        for (int i = 0; i < chunks.size(); i++) {
            final int chunkIndex = i; // 为lambda表达式创建final副本
            byte[] chunkData = chunks.get(i);
            String chunkHash = DigestUtils.sha256Hex(chunkData);

            // 检查块是否已存在(去重)
            FileChunk chunk = chunkRepository.findByChunkHash(chunkHash)
                    .orElseGet(() -> {
                        log.debug("Chunk {} (hash: {}) not found, uploading to storage", chunkIndex, chunkHash);
                        return uploadNewChunk(chunkHash, chunkData, userId, compress);
                    });

            // 如果块已存在,增加引用计数
            if (chunk.getChunkId() != null) {
                chunk.incrementRef();
                chunkRepository.save(chunk);
                log.debug("Chunk {} already exists, ref count: {}", chunkHash, chunk.getRefCount());
            }

            // 创建文件-块映射
            FileChunkMapping mapping = new FileChunkMapping();
            mapping.setFileId(fileId);
            mapping.setVersionNumber(versionNumber);
            mapping.setChunkId(chunk.getChunkId());
            mapping.setSequenceNumber(i);
            mapping.setOffsetInFile(offset);
            mappingRepository.save(mapping);

            offset += chunkData.length;
        }

        return chunks.size();
    }

    /**
     * 根据块映射重组文件。
     * 
     * @param fileId 文件ID
     * @param versionNumber 版本号
     * @return 文件输入流
     */
    @Transactional(readOnly = true)
    public InputStream assembleFile(String fileId, Integer versionNumber) {
        List<FileChunkMapping> mappings = mappingRepository
                .findByFileIdAndVersionNumberOrderBySequenceNumber(fileId, versionNumber);

        if (mappings.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件块映射不存在");
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            for (FileChunkMapping mapping : mappings) {
                FileChunk chunk = chunkRepository.findById(mapping.getChunkId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.STORAGE_ERROR, "块不存在"));

                // 从存储加载块数据
                try (InputStream chunkStream = storageService.loadFile(chunk.getStorageKey(), chunk.getCompressed())) {
                    chunkStream.transferTo(buffer);
                }
            }

            return new ByteArrayInputStream(buffer.toByteArray());
        } catch (IOException ex) {
            log.error("Failed to assemble file from chunks", ex);
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "文件组装失败", ex);
        }
    }

    /**
     * 删除文件的块映射,并清理不再使用的块。
     * 
     * @param fileId 文件ID
     */
    @Transactional
    public void deleteFileChunks(String fileId) {
        List<FileChunkMapping> mappings = mappingRepository.findByFileId(fileId);

        for (FileChunkMapping mapping : mappings) {
            FileChunk chunk = chunkRepository.findById(mapping.getChunkId()).orElse(null);
            if (chunk != null) {
                chunk.decrementRef();
                if (chunk.getRefCount() <= 0) {
                    // 引用计数为0,删除块
                    log.debug("Deleting chunk {} (hash: {}) as ref count is 0", chunk.getChunkId(), chunk.getChunkHash());
                    storageService.deleteFile(chunk.getStorageKey());
                    chunkRepository.delete(chunk);
                } else {
                    chunkRepository.save(chunk);
                }
            }
        }

        mappingRepository.deleteByFileId(fileId);
    }

    /**
     * 计算文件每个块的哈希值,用于差分同步。
     * 
     * @param fileData 文件数据
     * @return 块哈希列表
     */
    public List<String> calculateChunkHashes(byte[] fileData) {
        List<byte[]> chunks = splitIntoChunks(fileData);
        List<String> hashes = new ArrayList<>();
        for (byte[] chunk : chunks) {
            hashes.add(DigestUtils.sha256Hex(chunk));
        }
        return hashes;
    }

    /**
     * 将文件数据分割成固定大小的块。
     */
    private List<byte[]> splitIntoChunks(byte[] data) {
        List<byte[]> chunks = new ArrayList<>();
        int offset = 0;

        while (offset < data.length) {
            int remaining = data.length - offset;
            int chunkSize = Math.min(CHUNK_SIZE, remaining);

            byte[] chunk = new byte[chunkSize];
            System.arraycopy(data, offset, chunk, 0, chunkSize);
            chunks.add(chunk);

            offset += chunkSize;
        }

        return chunks;
    }

    /**
     * 上传新块到存储。
     */
    private FileChunk uploadNewChunk(String chunkHash, byte[] chunkData, String userId, boolean compress) {
        try {
            log.debug("Uploading new chunk: hash={}, size={}, compress={}", chunkHash, chunkData.length, compress);
            byte[] dataToUpload = chunkData;
            boolean actuallyCompressed = false;

            if (compress) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
                    gzip.write(chunkData);
                }
                dataToUpload = bos.toByteArray();
                actuallyCompressed = true;

                // 如果压缩后更大,使用原始数据
                if (dataToUpload.length >= chunkData.length) {
                    dataToUpload = chunkData;
                    actuallyCompressed = false;
                    log.debug("Compression not beneficial, using original data");
                }
            }

            String keyPrefix = "chunks/" + userId + "/" + chunkHash.substring(0, 2);
            String filename = chunkHash + ".chunk";
            log.debug("Uploading chunk to S3: keyPrefix={}, filename={}, size={}", keyPrefix, filename, dataToUpload.length);

            String storageKey = storageService.storeBytes(dataToUpload, keyPrefix, filename, actuallyCompressed);
            log.debug("Chunk uploaded successfully: storageKey={}", storageKey);

            FileChunk chunk = new FileChunk();
            chunk.setChunkHash(chunkHash);
            chunk.setStorageKey(storageKey);
            chunk.setChunkSize((long) chunkData.length);
            chunk.setCompressed(actuallyCompressed);
            chunk.setRefCount(1);

            return chunkRepository.save(chunk);
        } catch (IOException ex) {
            log.error("Failed to upload chunk", ex);
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "块上传失败", ex);
        }
    }
}
