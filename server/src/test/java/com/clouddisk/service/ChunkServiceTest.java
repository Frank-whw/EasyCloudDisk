package com.clouddisk.service;

import com.clouddisk.entity.FileChunk;
import com.clouddisk.entity.FileChunkMapping;
import com.clouddisk.repository.FileChunkMappingRepository;
import com.clouddisk.repository.FileChunkRepository;
import com.clouddisk.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChunkService 单元测试
 * 测试块级去重、分块存储、文件重组等逻辑
 */
@ExtendWith(MockitoExtension.class)
class ChunkServiceTest {

    @Mock
    private FileChunkRepository chunkRepository;

    @Mock
    private FileChunkMappingRepository mappingRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private ChunkService chunkService;

    private String fileId;
    private String userId;
    private byte[] testData;

    @BeforeEach
    void setUp() {
        fileId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        // 创建大于 4MB 的测试数据以触发分块
        testData = new byte[5 * 1024 * 1024]; // 5MB
        for (int i = 0; i < testData.length; i++) {
            testData[i] = (byte) (i % 256);
        }
    }

    @Test
    void testStoreFileInChunks_LargeFile_SplitsIntoChunks() {
        // Given
        when(chunkRepository.findByChunkHash(anyString())).thenReturn(Optional.empty());
        when(storageService.storeBytes(any(byte[].class), anyString(), anyString(), eq(true)))
                .thenReturn("oss-key-1", "oss-key-2");
        when(chunkRepository.save(any(FileChunk.class))).thenAnswer(invocation -> {
            FileChunk chunk = invocation.getArgument(0);
            if (chunk.getChunkId() == null) {
                chunk.setChunkId((long) (Math.random() * 1000000));
            }
            return chunk;
        });
        when(mappingRepository.save(any(FileChunkMapping.class))).thenAnswer(invocation -> {
            FileChunkMapping mapping = invocation.getArgument(0);
            if (mapping.getMappingId() == null) {
                mapping.setMappingId((long) (Math.random() * 1000000));
            }
            return mapping;
        });

        // When
        int chunkCount = chunkService.storeFileInChunks(fileId, 1, testData, userId, true);

        // Then
        assertTrue(chunkCount >= 2); // 5MB 应该被分成至少2个4MB块
        verify(storageService, atLeast(1)).storeBytes(any(byte[].class), anyString(), anyString(), eq(true));
        verify(chunkRepository, atLeast(1)).save(any(FileChunk.class));
        verify(mappingRepository, atLeast(1)).save(any(FileChunkMapping.class));
    }

    @Test
    void testStoreFileInChunks_ExistingChunk_IncrementsRefCount() {
        // Given
        String existingChunkHash = "existing-hash";
        FileChunk existingChunk = new FileChunk();
        existingChunk.setChunkId((long) (Math.random() * 1000000));
        existingChunk.setChunkHash(existingChunkHash);
        existingChunk.setRefCount(1);

        // 模拟前几个块已存在
        when(chunkRepository.findByChunkHash(anyString()))
                .thenReturn(Optional.of(existingChunk))
                .thenReturn(Optional.empty());

        when(storageService.storeBytes(any(byte[].class), anyString(), anyString(), eq(true)))
                .thenReturn("oss-key-new");
        when(chunkRepository.save(any(FileChunk.class))).thenAnswer(invocation -> {
            FileChunk chunk = invocation.getArgument(0);
            if (chunk.getChunkId() == null) {
                chunk.setChunkId((long) (Math.random() * 1000000));
            }
            return chunk;
        });
        when(mappingRepository.save(any(FileChunkMapping.class))).thenAnswer(invocation -> {
            FileChunkMapping mapping = invocation.getArgument(0);
            if (mapping.getMappingId() == null) {
                mapping.setMappingId((long) (Math.random() * 1000000));
            }
            return mapping;
        });

        // When
        chunkService.storeFileInChunks(fileId, 1, testData, userId, true);

        // Then
        // 验证已存在的块的引用计数被增加
        verify(chunkRepository).save(argThat(chunk -> 
            chunk.getChunkHash().equals(existingChunkHash) && chunk.getRefCount() == 2));
    }

    @Test
    void testAssembleFile_Success() throws Exception {
        // Given
        Long chunk1Id = 1L;
        Long chunk2Id = 2L;

        FileChunkMapping mapping1 = new FileChunkMapping();
        mapping1.setFileId(fileId);
        mapping1.setChunkId(chunk1Id);
        mapping1.setSequenceNumber(0);

        FileChunkMapping mapping2 = new FileChunkMapping();
        mapping2.setFileId(fileId);
        mapping2.setChunkId(chunk2Id);
        mapping2.setSequenceNumber(1);

        FileChunk chunk1 = new FileChunk();
        chunk1.setChunkId(chunk1Id);
        chunk1.setStorageKey("oss-key-1");
        chunk1.setCompressed(false);
        chunk1.setChunkSize(1024L);

        FileChunk chunk2 = new FileChunk();
        chunk2.setChunkId(chunk2Id);
        chunk2.setStorageKey("oss-key-2");
        chunk2.setCompressed(false);
        chunk2.setChunkSize(512L);

        when(mappingRepository.findByFileIdAndVersionNumberOrderBySequenceNumber(fileId, 1))
                .thenReturn(List.of(mapping1, mapping2));
        when(chunkRepository.findById(chunk1Id)).thenReturn(Optional.of(chunk1));
        when(chunkRepository.findById(chunk2Id)).thenReturn(Optional.of(chunk2));
        when(storageService.loadFile("oss-key-1", false))
                .thenReturn(new ByteArrayInputStream(new byte[1024]));
        when(storageService.loadFile("oss-key-2", false))
                .thenReturn(new ByteArrayInputStream(new byte[512]));

        // When
        InputStream result = chunkService.assembleFile(fileId, 1);

        // Then
        assertNotNull(result);
        verify(storageService, times(1)).loadFile("oss-key-1", false);
        verify(storageService, times(1)).loadFile("oss-key-2", false);
    }

    @Test
    void testDeleteFileChunks_DecrementsRefCount() {
        // Given
        Long chunkId = 1L;
        FileChunkMapping mapping = new FileChunkMapping();
        mapping.setFileId(fileId);
        mapping.setChunkId(chunkId);

        FileChunk chunk = new FileChunk();
        chunk.setChunkId(chunkId);
        chunk.setRefCount(2); // 被2个文件引用
        chunk.setStorageKey("oss-key");

        when(mappingRepository.findByFileId(fileId)).thenReturn(List.of(mapping));
        when(chunkRepository.findById(chunkId)).thenReturn(Optional.of(chunk));
        when(chunkRepository.save(any(FileChunk.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });

        // When
        chunkService.deleteFileChunks(fileId);

        // Then
        // 验证引用计数减1
        verify(chunkRepository).save(argThat(c -> c.getRefCount() == 1));
        verify(storageService, never()).deleteFile(anyString()); // 不应该删除，因为 refCount > 0
    }

    @Test
    void testDeleteFileChunks_RefCountZero_DeletesChunk() {
        // Given
        Long chunkId = 1L;
        FileChunkMapping mapping = new FileChunkMapping();
        mapping.setFileId(fileId);
        mapping.setChunkId(chunkId);

        FileChunk chunk = new FileChunk();
        chunk.setChunkId(chunkId);
        chunk.setRefCount(1); // 只被1个文件引用
        chunk.setStorageKey("oss-key");

        when(mappingRepository.findByFileId(fileId)).thenReturn(List.of(mapping));
        when(chunkRepository.findById(chunkId)).thenReturn(Optional.of(chunk));

        // When
        chunkService.deleteFileChunks(fileId);

        // Then
        // 验证引用计数减为0后删除存储（refCount从1减为0后直接删除，不会save）
        verify(storageService, times(1)).deleteFile("oss-key");
        verify(chunkRepository, times(1)).delete(chunk);
        verify(chunkRepository, never()).save(any(FileChunk.class)); // refCount为0时直接删除，不save
    }

    @Test
    void testCalculateChunkHashes_Success() {
        // Given
        byte[] smallData = new byte[1024];
        for (int i = 0; i < smallData.length; i++) {
            smallData[i] = (byte) i;
        }

        // When
        List<String> hashes = chunkService.calculateChunkHashes(smallData);

        // Then
        assertNotNull(hashes);
        assertFalse(hashes.isEmpty());
        // 小于4MB的数据应该只有一个块
        assertEquals(1, hashes.size());
        assertNotNull(hashes.get(0));
    }
}

