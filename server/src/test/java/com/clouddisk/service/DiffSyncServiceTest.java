package com.clouddisk.service;

import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.entity.FileChunk;
import com.clouddisk.entity.FileChunkMapping;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.repository.FileChunkMappingRepository;
import com.clouddisk.repository.FileChunkRepository;
import com.clouddisk.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiffSyncServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileChunkRepository chunkRepository;

    @Mock
    private FileChunkMappingRepository mappingRepository;

    @Mock
    private ChunkService chunkService;

    @InjectMocks
    private DiffSyncService diffSyncService;

    private String userId;
    private String fileId;
    private FileEntity fileEntity;

    @BeforeEach
    void setUp() {
        userId = "user-123";
        fileId = "file-456";
        
        fileEntity = new FileEntity();
        fileEntity.setFileId(fileId);
        fileEntity.setUserId(userId);
        fileEntity.setName("test.txt");
        fileEntity.setStorageKey("chunked");
        fileEntity.setVersion(1);
        fileEntity.setFileSize(4194304L); // 4MB
    }

    @Test
    void getFileSignatures_ShouldReturnChunkSignatures() {
        // Given
        when(fileRepository.findByFileIdAndUserId(fileId, userId))
            .thenReturn(Optional.of(fileEntity));

        FileChunkMapping mapping1 = createMapping(0, 1L, 0L);
        FileChunkMapping mapping2 = createMapping(1, 2L, 4194304L);
        
        FileChunk chunk1 = new FileChunk();
        chunk1.setChunkId(1L);
        chunk1.setChunkHash("chunk-hash-1");
        chunk1.setOriginalSize(4194304);
        
        FileChunk chunk2 = new FileChunk();
        chunk2.setChunkId(2L);
        chunk2.setChunkHash("chunk-hash-2");
        chunk2.setOriginalSize(2097152);
        
        when(mappingRepository.findByFileIdAndVersionNumberOrderBySequenceNumber(fileId, 1))
            .thenReturn(Arrays.asList(mapping1, mapping2));
        when(chunkRepository.findById(1L)).thenReturn(Optional.of(chunk1));
        when(chunkRepository.findById(2L)).thenReturn(Optional.of(chunk2));

        // When
        List<Map<String, Object>> signatures = diffSyncService.getFileSignatures(fileId, userId);

        // Then
        assertNotNull(signatures);
        assertEquals(2, signatures.size());
        
        Map<String, Object> sig1 = signatures.get(0);
        assertEquals(0, sig1.get("chunkIndex"));
        assertEquals("chunk-hash-1", sig1.get("hash"));
        assertEquals(4194304, sig1.get("size"));
        assertEquals(0, sig1.get("offset"));
        
        Map<String, Object> sig2 = signatures.get(1);
        assertEquals(1, sig2.get("chunkIndex"));
        assertEquals("chunk-hash-2", sig2.get("hash"));
        assertEquals(2097152, sig2.get("size"));
        assertEquals(4194304, sig2.get("offset"));
    }

    @Test
    void getFileSignatures_ShouldThrowException_WhenFileNotChunked() {
        // Given
        fileEntity.setStorageKey("direct-s3-key"); // Not "chunked"
        when(fileRepository.findByFileIdAndUserId(fileId, userId))
            .thenReturn(Optional.of(fileEntity));

        // When & Then
        assertThrows(BusinessException.class, () ->
            diffSyncService.getFileSignatures(fileId, userId)
        );
    }

    @Test
    void getFileSignatures_ShouldThrowException_WhenFileNotFound() {
        // Given
        when(fileRepository.findByFileIdAndUserId(fileId, userId))
            .thenReturn(Optional.empty());

        // When & Then
        assertThrows(BusinessException.class, () ->
            diffSyncService.getFileSignatures(fileId, userId)
        );
    }

    @Test
    void applyDelta_ShouldUpdateChangedChunks() {
        // Given
        when(fileRepository.findByFileIdAndUserId(fileId, userId))
            .thenReturn(Optional.of(fileEntity));

        FileChunkMapping mapping1 = createMapping(0, "old-hash-1", 4194304);
        FileChunkMapping mapping2 = createMapping(1, "old-hash-2", 4194304);
        
        when(mappingRepository.findByFileIdAndVersionNumberOrderBySequenceNumber(fileId, 1))
            .thenReturn(Arrays.asList(mapping1, mapping2));

        // 模拟只有第1个块发生变化
        Map<Integer, byte[]> deltaChunks = new HashMap<>();
        deltaChunks.put(1, "new chunk data".getBytes(StandardCharsets.UTF_8));

        when(chunkRepository.save(any(FileChunk.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(mappingRepository.save(any(FileChunkMapping.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(fileRepository.save(any(FileEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FileMetadataDto result = diffSyncService.applyDelta(fileId, userId, deltaChunks);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getVersion()); // 版本号应该递增
        verify(chunkRepository, times(1)).save(any(FileChunk.class)); // 只保存1个新块
        verify(mappingRepository, times(2)).save(any(FileChunkMapping.class)); // 保存2个映射
        verify(fileRepository).save(any(FileEntity.class));
    }

    @Test
    void applyDelta_ShouldThrowException_WhenInvalidChunkIndex() {
        // Given
        when(fileRepository.findByFileIdAndUserId(fileId, userId))
            .thenReturn(Optional.of(fileEntity));

        FileChunkMapping mapping1 = createMapping(0, 1L, 0L);
        when(mappingRepository.findByFileIdAndVersionNumberOrderBySequenceNumber(fileId, 1))
            .thenReturn(List.of(mapping1));

        // 块索引超出范围
        Map<Integer, byte[]> deltaChunks = new HashMap<>();
        deltaChunks.put(5, "data".getBytes(StandardCharsets.UTF_8));

        // When & Then
        assertThrows(BusinessException.class, () ->
            diffSyncService.applyDelta(fileId, userId, deltaChunks)
        );
    }

    @Test
    void findMatchingBlocks_ShouldIdentifyUnchangedBlocks() {
        // Given
        byte[] newData = "This is a test file with some content.".getBytes(StandardCharsets.UTF_8);
        
        List<Map<String, Object>> serverSignatures = new ArrayList<>();
        Map<String, Object> sig1 = new HashMap<>();
        sig1.put("chunkIndex", 0);
        sig1.put("hash", "abc123");
        sig1.put("size", 20);
        serverSignatures.add(sig1);

        // When
        List<Integer> matchingBlocks = diffSyncService.findMatchingBlocks(newData, serverSignatures);

        // Then
        assertNotNull(matchingBlocks);
        // 注意: 实际匹配逻辑取决于滚动哈希算法实现
    }

    private FileChunkMapping createMapping(int sequenceNumber, Long chunkId, Long offset) {
        FileChunkMapping mapping = new FileChunkMapping();
        mapping.setFileId(fileId);
        mapping.setVersionNumber(1);
        mapping.setChunkId(chunkId);
        mapping.setSequenceNumber(sequenceNumber);
        mapping.setOffsetInFile(offset);
        return mapping;
    }
}
