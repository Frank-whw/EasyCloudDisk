package com.clouddisk.file.service;

import com.clouddisk.common.dto.FileResponse;
import com.clouddisk.common.dto.FileUploadResponse;
import com.clouddisk.file.entity.File;
import com.clouddisk.common.exception.BusinessException;
import com.clouddisk.file.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private FileService fileService;

    private UUID userId;
    private UUID fileId;
    private File testFile;
    private MockMultipartFile testMultipartFile;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        fileId = UUID.randomUUID();
        
        testFile = new File(
            userId,
            "test.txt",
            "/",
            "s3-key-test",
            1024L,
            "hash123"
        );
        testFile.setFileId(fileId);
        testFile.setCreatedAt(LocalDateTime.now());
        testFile.setUpdatedAt(LocalDateTime.now());

        testMultipartFile = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "Hello World".getBytes()
        );
    }

    @Test
    void getUserFiles_ShouldReturnUserFiles() {
        // Arrange
        when(fileRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(List.of(testFile));

        // Act
        List<FileResponse> result = fileService.getUserFiles(userId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(fileId, result.get(0).getFileId());
        assertEquals("test.txt", result.get(0).getName());
        
        verify(fileRepository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void getUserFiles_ShouldReturnEmptyListWhenNoFiles() {
        // Arrange
        when(fileRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(List.of());

        // Act
        List<FileResponse> result = fileService.getUserFiles(userId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(fileRepository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void uploadFile_ShouldUploadSuccessfully() throws IOException, NoSuchAlgorithmException {
        // Arrange
        when(fileRepository.findByUserIdAndContentHash(any(), any()))
            .thenReturn(Optional.empty());
        when(s3Service.uploadFile(any(), any())).thenReturn("s3://test-url");
        when(fileRepository.save(any(File.class))).thenReturn(testFile);

        // Act
        FileUploadResponse result = fileService.uploadFile(userId, testMultipartFile, "/", null);

        // Assert
        assertNotNull(result);
        assertEquals(fileId, result.getFileId());
        assertEquals("test.txt", result.getFileName());
        
        verify(fileRepository).findByUserIdAndContentHash(userId, anyString());
        verify(s3Service).uploadFile(any(), any());
        verify(fileRepository).save(any(File.class));
    }

    @Test
    void uploadFile_ShouldThrowBusinessExceptionWhenFileExists() {
        // Arrange
        when(fileRepository.findByUserIdAndContentHash(any(), any()))
            .thenReturn(Optional.of(testFile));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () ->
            fileService.uploadFile(userId, testMultipartFile, "/", "existing-hash")
        );
        
        assertEquals("文件重复", exception.getMessage());
        assertEquals(409, exception.getCode());
        
        verify(fileRepository).findByUserIdAndContentHash(userId, "existing-hash");
        verify(s3Service, never()).uploadFile(any(), any());
        verify(fileRepository, never()).save(any());
    }

    @Test
    void uploadFile_ShouldUseProvidedContentHash() throws IOException, NoSuchAlgorithmException {
        // Arrange
        when(fileRepository.findByUserIdAndContentHash(any(), any()))
            .thenReturn(Optional.empty());
        when(s3Service.uploadFile(any(), any())).thenReturn("s3://test-url");
        when(fileRepository.save(any(File.class))).thenReturn(testFile);

        // Act
        FileUploadResponse result = fileService.uploadFile(userId, testMultipartFile, "/", "provided-hash");

        // Assert
        assertNotNull(result);
        verify(fileRepository).findByUserIdAndContentHash(userId, "provided-hash");
    }

    @Test
    void deleteFile_ShouldDeleteSuccessfully() {
        // Arrange
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(testFile));
        doNothing().when(s3Service).deleteFile(any());
        doNothing().when(fileRepository).delete(any());

        // Act
        assertDoesNotThrow(() -> fileService.deleteFile(userId, fileId));

        // Assert
        verify(fileRepository).findById(fileId);
        verify(s3Service).deleteFile("s3-key-test");
        verify(fileRepository).delete(testFile);
    }

    @Test
    void deleteFile_ShouldThrowExceptionWhenFileNotFound() {
        // Arrange
        when(fileRepository.findById(fileId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            fileService.deleteFile(userId, fileId)
        );
        
        assertEquals("文件不存在", exception.getMessage());
        
        verify(fileRepository).findById(fileId);
        verify(s3Service, never()).deleteFile(any());
        verify(fileRepository, never()).delete(any());
    }

    @Test
    void deleteFile_ShouldThrowExceptionWhenNotOwner() {
        // Arrange
        UUID otherUserId = UUID.randomUUID();
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(testFile));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            fileService.deleteFile(otherUserId, fileId)
        );
        
        assertEquals("无权删除此文件", exception.getMessage());
        
        verify(fileRepository).findById(fileId);
        verify(s3Service, never()).deleteFile(any());
        verify(fileRepository, never()).delete(any());
    }

    @Test
    void downloadFile_ShouldDownloadSuccessfully() {
        // Arrange
        byte[] fileContent = "Hello World".getBytes();
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(testFile));
        when(s3Service.downloadFile("s3-key-test")).thenReturn(fileContent);

        // Act
        byte[] result = fileService.downloadFile(userId, fileId);

        // Assert
        assertArrayEquals(fileContent, result);
        
        verify(fileRepository).findById(fileId);
        verify(s3Service).downloadFile("s3-key-test");
    }

    @Test
    void downloadFile_ShouldThrowExceptionWhenFileNotFound() {
        // Arrange
        when(fileRepository.findById(fileId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            fileService.downloadFile(userId, fileId)
        );
        
        assertEquals("文件不存在", exception.getMessage());
        
        verify(fileRepository).findById(fileId);
        verify(s3Service, never()).downloadFile(any());
    }

    @Test
    void downloadFile_ShouldThrowExceptionWhenNotOwner() {
        // Arrange
        UUID otherUserId = UUID.randomUUID();
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(testFile));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            fileService.downloadFile(otherUserId, fileId)
        );
        
        assertEquals("无权访问此文件", exception.getMessage());
        
        verify(fileRepository).findById(fileId);
        verify(s3Service, never()).downloadFile(any());
    }

    @Test
    void openFileStream_ShouldReturnInputStream() {
        // Arrange
        InputStream mockInputStream = new ByteArrayInputStream("Hello World".getBytes());
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(testFile));
        when(s3Service.downloadFileStream("s3-key-test")).thenReturn(mockInputStream);

        // Act
        InputStream result = fileService.openFileStream(userId, fileId);

        // Assert
        assertNotNull(result);
        
        verify(fileRepository).findById(fileId);
        verify(s3Service).downloadFileStream("s3-key-test");
    }

    @Test
    void getFileInfo_ShouldReturnFileInfo() {
        // Arrange
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(testFile));

        // Act
        FileResponse result = fileService.getFileInfo(userId, fileId);

        // Assert
        assertNotNull(result);
        assertEquals(fileId, result.getFileId());
        assertEquals("test.txt", result.getName());
        assertEquals("/", result.getFilePath());
        assertEquals(1024L, result.getFileSize());
        
        verify(fileRepository).findById(fileId);
    }

    @Test
    void getFileInfo_ShouldThrowExceptionWhenNotOwner() {
        // Arrange
        UUID otherUserId = UUID.randomUUID();
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(testFile));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            fileService.getFileInfo(otherUserId, fileId)
        );
        
        assertEquals("无权访问此文件", exception.getMessage());
        
        verify(fileRepository).findById(fileId);
    }

    @Test
    void calculateFileHash_ShouldCalculateCorrectHash() throws IOException, NoSuchAlgorithmException {
        // Arrange
        String expectedHash = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e";
        
        // Act
        String result = fileService.calculateFileHash(testMultipartFile);

        // Assert
        assertEquals(expectedHash, result);
    }

    @Test
    void generateS3Key_ShouldGenerateValidKey() {
        // Act
        String result = fileService.generateS3Key(userId, "test file.txt");

        // Assert
        assertTrue(result.startsWith("user-" + userId));
        assertTrue(result.contains("test_file.txt"));
        assertTrue(result.matches("^user-[0-9a-f-]+/\\d+-test_file.txt$"));
    }
}