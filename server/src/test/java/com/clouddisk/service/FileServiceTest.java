package com.clouddisk.service;

import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.entity.FileVersion;
import com.clouddisk.entity.User;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.FileRepository;
import com.clouddisk.repository.FileVersionRepository;
import com.clouddisk.repository.UserRepository;
import com.clouddisk.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileService 单元测试
 * 测试文件上传、下载、删除等核心业务逻辑
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileVersionRepository fileVersionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private ChunkService chunkService;

    @InjectMocks
    private FileService fileService;

    private String userId;
    private User testUser;
    private MultipartFile testFile;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        testUser = new User();
        testUser.setUserId(userId);
        testUser.setEmail("test@example.com");

        testFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Hello, Cloud Disk!".getBytes()
        );
    }

    @Test
    void testUpload_Success_NewFile() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileRepository.findByUserIdAndDirectoryPathAndName(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            if (entity.getFileId() == null) {
                entity.setFileId(UUID.randomUUID().toString());
            }
            return entity;
        });

        // When
        FileMetadataDto result = fileService.upload(testFile, "/test", userId);

        // Then
        assertNotNull(result);
        assertNotNull(result.getFileId());
        assertEquals("test.txt", result.getName());
        verify(chunkService, times(1)).storeFileInChunks(anyString(), anyInt(), any(byte[].class), anyString(), eq(true));
        verify(fileVersionRepository, times(1)).save(any(FileVersion.class));
    }

    @Test
    void testUpload_EmptyFile_ThrowsException() {
        // Given
        MultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileService.upload(emptyFile, "/test", userId);
        });
        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals("文件不能为空", exception.getMessage());
        verify(fileRepository, never()).save(any());
    }

    @Test
    void testUpload_NoFileName_ThrowsException() {
        // Given
        MultipartFile noNameFile = new MockMultipartFile("file", "", "text/plain", "content".getBytes());
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileService.upload(noNameFile, "/test", userId);
        });
        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals("文件名不能为空", exception.getMessage());
    }

    @Test
    void testUpload_UserNotFound_ThrowsException() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileService.upload(testFile, "/test", userId);
        });
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(fileRepository, never()).save(any());
    }

    @Test
    void testUpload_FileReadIOException_ThrowsException() throws IOException {
        // Given
        MultipartFile failingFile = mock(MultipartFile.class);
        when(failingFile.isEmpty()).thenReturn(false);
        when(failingFile.getOriginalFilename()).thenReturn("test.txt");
        when(failingFile.getBytes()).thenThrow(new IOException("Read error"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileRepository.findByUserIdAndDirectoryPathAndName(any(), any(), any()))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileService.upload(failingFile, "/test", userId);
        });
        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals("读取文件失败", exception.getMessage());
    }

    @Test
    void testUpload_ExistingFile_UpdatesVersion() {
        // Given
        FileEntity existingFile = new FileEntity();
        existingFile.setFileId(UUID.randomUUID().toString());
        existingFile.setUserId(userId);
        existingFile.setName("test.txt");
        existingFile.setVersion(1);
        existingFile.setStorageKey("old-key");
        existingFile.setFileSize(1024L); // 设置文件大小避免NPE

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileRepository.findByUserIdAndDirectoryPathAndName(userId, "/test", "test.txt"))
                .thenReturn(Optional.of(existingFile));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FileMetadataDto result = fileService.upload(testFile, "/test", userId);

        // Then
        assertNotNull(result);
        verify(fileVersionRepository, times(2)).save(any(FileVersion.class)); // 保存旧版本和新版本
        verify(fileRepository).save(argThat(entity -> entity.getVersion() == 2));
    }

    @Test
    void testListFiles_Success() {
        // Given
        FileEntity file1 = new FileEntity();
        file1.setFileId(UUID.randomUUID().toString());
        file1.setName("file1.txt");
        file1.setDirectory(false);
        file1.setFileSize(1024L); // 设置文件大小避免NPE
        file1.setDirectoryPath("/");

        FileEntity dir1 = new FileEntity();
        dir1.setFileId(UUID.randomUUID().toString());
        dir1.setName("dir1");
        dir1.setDirectory(true);
        dir1.setFileSize(0L); // 目录大小设为0
        dir1.setDirectoryPath("/");

        when(fileRepository.findAllByUserId(userId)).thenReturn(List.of(file1, dir1));

        // When
        List<FileMetadataDto> result = fileService.listFiles(userId, "/");

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        // 目录应该排在前面
        assertTrue(result.get(0).isDirectory());
        assertFalse(result.get(1).isDirectory());
    }

    @Test
    void testDelete_Success() {
        // Given
        String fileId = UUID.randomUUID().toString();
        FileEntity file = new FileEntity();
        file.setFileId(fileId);
        file.setUserId(userId);
        file.setStorageKey("chunked");
        file.setDirectory(false);
        file.setFileSize(1024L); // 设置文件大小避免NPE

        when(fileRepository.findByFileIdAndUserId(fileId, userId))
                .thenReturn(Optional.of(file));

        // When
        fileService.delete(fileId, userId);

        // Then
        verify(chunkService, times(1)).deleteFileChunks(fileId);
        verify(fileRepository, times(1)).delete(file);
    }

    @Test
    void testDelete_FileNotFound_ThrowsException() {
        // Given
        String fileId = UUID.randomUUID().toString();
        when(fileRepository.findByFileIdAndUserId(fileId, userId))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileService.delete(fileId, userId);
        });
        assertEquals(ErrorCode.FILE_NOT_FOUND, exception.getErrorCode());
        verify(chunkService, never()).deleteFileChunks(anyString());
    }

    @Test
    void testDelete_DifferentUser_ThrowsException() {
        // Given
        String fileId = UUID.randomUUID().toString();
        String otherUserId = UUID.randomUUID().toString();
        when(fileRepository.findByFileIdAndUserId(fileId, userId))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileService.delete(fileId, userId);
        });
        assertEquals(ErrorCode.FILE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void testDownload_Success() throws IOException {
        // Given
        String fileId = UUID.randomUUID().toString();
        FileEntity file = new FileEntity();
        file.setFileId(fileId);
        file.setUserId(userId);
        file.setDirectory(false);
        file.setStorageKey("chunked");
        file.setName("test.txt");
        file.setFileSize(1024L); // 设置文件大小避免NPE
        file.setVersion(1); // 设置版本号

        InputStream mockStream = new ByteArrayInputStream("file content".getBytes());
        when(fileRepository.findByFileIdAndUserId(fileId, userId))
                .thenReturn(Optional.of(file));
        when(chunkService.assembleFile(fileId, 1))
                .thenReturn(mockStream);

        // When
        ResponseEntity<Resource> result = fileService.download(fileId, userId);

        // Then
        assertNotNull(result);
        assertEquals(200, result.getStatusCodeValue());
        assertNotNull(result.getBody());
        verify(chunkService, times(1)).assembleFile(fileId, file.getVersion());
    }

    @Test
    void testDownload_Directory_ThrowsException() {
        // Given
        String fileId = UUID.randomUUID().toString();
        FileEntity directory = new FileEntity();
        directory.setFileId(fileId);
        directory.setUserId(userId);
        directory.setDirectory(true);

        when(fileRepository.findByFileIdAndUserId(fileId, userId))
                .thenReturn(Optional.of(directory));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileService.download(fileId, userId);
        });
        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals("目录无法下载", exception.getMessage());
    }
}

