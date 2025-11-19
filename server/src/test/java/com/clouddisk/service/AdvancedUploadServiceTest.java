package com.clouddisk.service;

import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.dto.UploadSessionDto;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.entity.UploadSession;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.repository.FileRepository;
import com.clouddisk.repository.UploadSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdvancedUploadServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private UploadSessionRepository uploadSessionRepository;

    @Mock
    private FileService fileService;

    @Mock
    private ChunkService chunkService;

    @InjectMocks
    private AdvancedUploadService advancedUploadService;

    private String userId;
    private String fileHash;

    @BeforeEach
    void setUp() {
        userId = "user-123";
        fileHash = "abc123hash";
    }

    @Test
    void checkQuickUpload_ShouldReturnTrue_WhenFileExists() {
        // Given
        FileEntity existingFile = new FileEntity();
        existingFile.setContentHash(fileHash);
        existingFile.setStorageKey("chunked");
        
        when(fileRepository.findAll()).thenReturn(List.of(existingFile));

        // When
        boolean canQuickUpload = advancedUploadService.checkQuickUpload(fileHash, userId);

        // Then
        assertTrue(canQuickUpload);
        verify(fileRepository).findAll();
    }

    @Test
    void checkQuickUpload_ShouldReturnFalse_WhenFileNotExists() {
        // Given
        when(fileRepository.findAll()).thenReturn(List.of());

        // When
        boolean canQuickUpload = advancedUploadService.checkQuickUpload(fileHash, userId);

        // Then
        assertFalse(canQuickUpload);
    }

    @Test
    void quickUpload_ShouldCreateFileWithExistingChunks() {
        // Given
        String fileName = "test.pdf";
        String path = "/documents";
        
        FileEntity sourceFile = new FileEntity();
        sourceFile.setFileId("source-file-id");
        sourceFile.setContentHash(fileHash);
        sourceFile.setStorageKey("chunked");
        sourceFile.setFileSize(1024000L);
        sourceFile.setVersion(1);
        
        when(fileRepository.findAll()).thenReturn(List.of(sourceFile));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FileMetadataDto result = advancedUploadService.quickUpload(fileHash, fileName, path, userId);

        // Then
        assertNotNull(result);
        assertEquals(fileName, result.getName());
        verify(chunkService).copyChunkReferences(eq("source-file-id"), anyString(), eq(1));
        verify(fileRepository).save(any(FileEntity.class));
    }

    @Test
    void quickUpload_ShouldThrowException_WhenFileNotFound() {
        // Given
        when(fileRepository.findAll()).thenReturn(List.of());

        // When & Then
        assertThrows(BusinessException.class, () ->
            advancedUploadService.quickUpload(fileHash, "test.pdf", "/", userId)
        );
    }

    @Test
    void initResumableUpload_ShouldCreateSession() {
        // Given
        String fileName = "large-file.zip";
        String path = "/uploads";
        Long fileSize = 104857600L; // 100MB
        
        when(uploadSessionRepository.save(any(UploadSession.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UploadSessionDto result = advancedUploadService.initResumableUpload(fileName, path, fileSize, userId);

        // Then
        assertNotNull(result);
        assertNotNull(result.getSessionId());
        assertEquals(50, result.getTotalChunks()); // 100MB / 2MB = 50 chunks
        assertEquals(0, result.getUploadedChunks());
        assertEquals("ACTIVE", result.getStatus());
        verify(uploadSessionRepository).save(any(UploadSession.class));
    }

    @Test
    void uploadChunk_ShouldUpdateSession() throws Exception {
        // Given
        String sessionId = "session-123";
        Integer chunkIndex = 0;
        
        UploadSession session = new UploadSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTotalChunks(10);
        session.setUploadedChunks(0);
        session.setStatus("ACTIVE");
        session.setUploadedChunkIndexes("");
        
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getBytes()).thenReturn(new byte[2097152]); // 2MB
        
        when(uploadSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UploadSessionDto result = advancedUploadService.uploadChunk(sessionId, chunkIndex, mockFile, userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getUploadedChunks());
        verify(uploadSessionRepository).save(any(UploadSession.class));
    }

    @Test
    void uploadChunk_ShouldThrowException_WhenSessionNotFound() {
        // Given
        String sessionId = "non-existent";
        MultipartFile mockFile = mock(MultipartFile.class);
        
        when(uploadSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(BusinessException.class, () ->
            advancedUploadService.uploadChunk(sessionId, 0, mockFile, userId)
        );
    }

    @Test
    void uploadChunk_ShouldThrowException_WhenInvalidChunkIndex() {
        // Given
        String sessionId = "session-123";
        
        UploadSession session = new UploadSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTotalChunks(10);
        
        when(uploadSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        MultipartFile mockFile = mock(MultipartFile.class);

        // When & Then
        assertThrows(BusinessException.class, () ->
            advancedUploadService.uploadChunk(sessionId, 15, mockFile, userId) // Invalid index
        );
    }

    @Test
    void completeResumableUpload_ShouldMergeChunksAndCreateFile() {
        // Given
        String sessionId = "session-123";
        
        UploadSession session = new UploadSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setFileName("test.zip");
        session.setFilePath("/");
        session.setTotalChunks(3);
        session.setUploadedChunks(3);
        session.setUploadedChunkIndexes("0,1,2");
        session.setStatus("ACTIVE");
        
        when(uploadSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FileMetadataDto result = advancedUploadService.completeResumableUpload(sessionId, userId);

        // Then
        assertNotNull(result);
        assertEquals("test.zip", result.getName());
        verify(uploadSessionRepository).delete(session);
    }

    @Test
    void completeResumableUpload_ShouldThrowException_WhenIncomplete() {
        // Given
        String sessionId = "session-123";
        
        UploadSession session = new UploadSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTotalChunks(10);
        session.setUploadedChunks(5);
        
        when(uploadSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // When & Then
        assertThrows(BusinessException.class, () ->
            advancedUploadService.completeResumableUpload(sessionId, userId)
        );
    }

    @Test
    void listSessions_ShouldReturnUserSessions() {
        // Given
        UploadSession session1 = createSession("session-1", "file1.txt", 10, 5);
        UploadSession session2 = createSession("session-2", "file2.txt", 20, 20);
        
        when(uploadSessionRepository.findAllByUserId(userId))
            .thenReturn(Arrays.asList(session1, session2));

        // When
        List<UploadSessionDto> results = advancedUploadService.listSessions(userId);

        // Then
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("session-1", results.get(0).getSessionId());
        assertEquals("session-2", results.get(1).getSessionId());
    }

    @Test
    void cleanupExpiredSessions_ShouldRemoveOldSessions() {
        // Given
        UploadSession expiredSession = new UploadSession();
        expiredSession.setSessionId("expired-1");
        expiredSession.setCreatedAt(Instant.now().minusSeconds(86400 + 3600)); // 25 hours old
        
        when(uploadSessionRepository.findAll()).thenReturn(List.of(expiredSession));

        // When
        advancedUploadService.cleanupExpiredSessions();

        // Then
        verify(uploadSessionRepository).delete(expiredSession);
    }

    private UploadSession createSession(String sessionId, String fileName, int totalChunks, int uploadedChunks) {
        UploadSession session = new UploadSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setFileName(fileName);
        session.setFilePath("/");
        session.setTotalChunks(totalChunks);
        session.setUploadedChunks(uploadedChunks);
        session.setStatus("ACTIVE");
        session.setCreatedAt(Instant.now());
        return session;
    }
}
