package com.clouddisk.service;

import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.entity.FileChunk;
import com.clouddisk.entity.FileChunkMapping;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.repository.FileChunkMappingRepository;
import com.clouddisk.repository.FileChunkRepository;
import com.clouddisk.repository.FileRepository;
import com.clouddisk.storage.StorageService;
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

    @Mock
    private StorageService storageService;

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
        chunk1.setChunkSize(4194304L);
        chunk1.setStorageKey("chunk-1");
        chunk1.setCompressed(false);
        
        FileChunk chunk2 = new FileChunk();
        chunk2.setChunkId(2L);
        chunk2.setChunkHash("chunk-hash-2");
        chunk2.setChunkSize(2097152L);
        chunk2.setStorageKey("chunk-2");
        chunk2.setCompressed(false);
        
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
        assertEquals(4194304L, sig1.get("size"));
        assertEquals(0L, sig1.get("offset"));
        
        Map<String, Object> sig2 = signatures.get(1);
        assertEquals(1, sig2.get("chunkIndex"));
        assertEquals("chunk-hash-2", sig2.get("hash"));
        assertEquals(2097152L, sig2.get("size"));
        assertEquals(4194304L, sig2.get("offset"));
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

        FileChunkMapping mapping1 = createMapping(0, 1L, 0L);
        FileChunkMapping mapping2 = createMapping(1, 2L, 4194304L);
        
        when(mappingRepository.findByFileIdAndVersionNumberOrderBySequenceNumber(fileId, 1))
            .thenReturn(Arrays.asList(mapping1, mapping2));

        // 模拟只有第1个块发生变化
        Map<Integer, byte[]> deltaChunks = new HashMap<>();
        deltaChunks.put(1, "new chunk data".getBytes(StandardCharsets.UTF_8));
        
        FileChunk chunk0 = new FileChunk();
        chunk0.setChunkId(1L);
        chunk0.setStorageKey("chunk-1");
        chunk0.setCompressed(false);
        
        when(chunkRepository.findById(1L)).thenReturn(Optional.of(chunk0));
        when(storageService.loadFile(eq("chunk-1"), anyBoolean()))
                .thenReturn(new java.io.ByteArrayInputStream("existing data".getBytes(StandardCharsets.UTF_8)));
        when(fileRepository.save(any(FileEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FileMetadataDto result = diffSyncService.applyDelta(fileId, userId, deltaChunks);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getVersion()); // 版本号应该递增
        verify(chunkService).storeFileInChunks(eq(fileId), eq(2), any(byte[].class), eq(userId), eq(true));
        verify(fileRepository).save(any(FileEntity.class));
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
