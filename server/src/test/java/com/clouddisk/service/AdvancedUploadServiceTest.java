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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    void checkQuickUploadShouldReturnTrueWhenFileExists() {
        FileEntity file = new FileEntity();
        file.setFileId("file-1");
        file.setDirectory(false);
        when(fileRepository.findFirstByContentHash(fileHash)).thenReturn(Optional.of(file));

        boolean canQuickUpload = advancedUploadService.checkQuickUpload(fileHash, userId);

        assertTrue(canQuickUpload);
        verify(fileRepository).findFirstByContentHash(fileHash);
    }

    @Test
    void checkQuickUploadShouldReturnFalseWhenFileMissing() {
        when(fileRepository.findFirstByContentHash(fileHash)).thenReturn(Optional.empty());

        assertFalse(advancedUploadService.checkQuickUpload(fileHash, userId));
    }

    @Test
    void quickUploadShouldReuseStorageFromExistingFile() {
        FileEntity source = new FileEntity();
        source.setFileId("source-id");
        source.setDirectory(false);
        source.setStorageKey("files/source");
        source.setFileSize(1024L);
        source.setContentHash(fileHash);

        when(fileRepository.findFirstByContentHash(fileHash)).thenReturn(Optional.of(source));
        when(fileService.normalizePath("/docs")).thenReturn("/docs");
        when(fileRepository.findByUserIdAndDirectoryPathAndName(userId, "/docs", "copy.txt"))
                .thenReturn(Optional.empty());
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileMetadataDto dto = advancedUploadService.quickUpload(fileHash, "copy.txt", "/docs", userId);

        assertEquals("copy.txt", dto.getName());
        verify(fileRepository).save(any(FileEntity.class));
    }

    @Test
    void quickUploadShouldFailWhenSourceMissing() {
        when(fileRepository.findFirstByContentHash(fileHash)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class,
                () -> advancedUploadService.quickUpload(fileHash, "copy.txt", "/", userId));
    }

    @Test
    void initResumableUploadShouldPersistSession() {
        when(fileService.normalizePath("/uploads")).thenReturn("/uploads");
        when(uploadSessionRepository.save(any(UploadSession.class)))
                .thenAnswer(invocation -> {
                    UploadSession session = invocation.getArgument(0);
                    session.setSessionId("session-1");
                    return session;
                });

        UploadSessionDto dto = advancedUploadService.initResumableUpload("big.bin", "/uploads", 4L * 1024 * 1024, userId);

        assertEquals("session-1", dto.getSessionId());
        assertEquals(2, dto.getTotalChunks());
        assertEquals("ACTIVE", dto.getStatus());
    }

    @Test
    void uploadChunkShouldMarkChunkUploaded() throws Exception {
        UploadSession session = new UploadSession();
        session.setSessionId("session-1");
        session.setUserId(userId);
        session.setTotalChunks(3);
        session.setStatus("ACTIVE");

        MultipartFile chunk = mock(MultipartFile.class);
        when(chunk.getBytes()).thenReturn(new byte[10]);

        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", userId)).thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadSessionDto dto = advancedUploadService.uploadChunk("session-1", 1, chunk, userId);

        assertEquals(1, dto.getUploadedChunks());
        verify(uploadSessionRepository).save(session);
    }

    @Test
    void uploadChunkShouldRejectInvalidIndex() {
        UploadSession session = new UploadSession();
        session.setSessionId("session-1");
        session.setUserId(userId);
        session.setTotalChunks(2);

        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", userId)).thenReturn(Optional.of(session));
        MultipartFile chunk = mock(MultipartFile.class);

        assertThrows(BusinessException.class,
                () -> advancedUploadService.uploadChunk("session-1", 5, chunk, userId));
    }

    @Test
    void completeResumableUploadShouldCreateFileOnceAllChunksUploaded() {
        UploadSession session = new UploadSession();
        session.setSessionId("session-1");
        session.setUserId(userId);
        session.setFileName("done.zip");
        session.setFilePath("/");
        session.setFileSize(123L);
        session.setTotalChunks(2);
        session.getUploadedChunks().addAll(Set.of(0, 1));
        session.setStatus("ACTIVE");

        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", userId)).thenReturn(Optional.of(session));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileMetadataDto dto = advancedUploadService.completeResumableUpload("session-1", userId);

        assertEquals("done.zip", dto.getName());
        verify(fileRepository).save(any(FileEntity.class));
    }

    @Test
    void completeResumableUploadShouldFailWhenChunksMissing() {
        UploadSession session = new UploadSession();
        session.setSessionId("session-1");
        session.setUserId(userId);
        session.setTotalChunks(2);
        session.getUploadedChunks().add(0);

        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", userId)).thenReturn(Optional.of(session));

        assertThrows(BusinessException.class,
                () -> advancedUploadService.completeResumableUpload("session-1", userId));
    }

    @Test
    void listSessionsShouldMapEntitiesToDto() {
        UploadSession session = new UploadSession();
        session.setSessionId("session-1");
        session.setUserId(userId);
        session.setFileName("file.txt");
        session.setTotalChunks(3);
        session.getUploadedChunks().add(0);
        session.setStatus("ACTIVE");

        when(uploadSessionRepository.findAllByUserId(userId)).thenReturn(List.of(session));

        List<UploadSessionDto> dtos = advancedUploadService.listSessions(userId);

        assertEquals(1, dtos.size());
        assertEquals("session-1", dtos.get(0).getSessionId());
    }

    @Test
    void cleanExpiredSessionsShouldDeleteExpiredOnes() {
        UploadSession expired = new UploadSession();
        expired.setSessionId("expired");
        expired.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(uploadSessionRepository.findAllByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(List.of(expired));

        advancedUploadService.cleanExpiredSessions();

        verify(uploadSessionRepository).deleteAll(List.of(expired));
    }
}
