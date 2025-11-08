package com.clouddisk.service;

import com.clouddisk.dto.FileResponse;
import com.clouddisk.dto.FileUploadResponse;
import com.clouddisk.entity.File;
import com.clouddisk.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {
    
    private final FileRepository fileRepository;
    private final S3Service s3Service;
    
    /**
     * 获取用户的所有文件列表
     */
    public List<FileResponse> getUserFiles(UUID userId) {
        log.info("获取用户文件列表，用户ID: {}", userId);
        List<File> files = fileRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return files.stream()
                .map(this::convertToFileResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * 上传文件
     */
    @Transactional
    public FileUploadResponse uploadFile(UUID userId, MultipartFile file, String filePath, String contentHashOpt) {
        log.info("上传文件，用户ID: {}, 文件名: {}, 路径: {}", userId, file.getOriginalFilename(), filePath);
        
        try {
            // 使用客户端提供的内容哈希（如果有），否则计算
            String contentHash = contentHashOpt != null && !contentHashOpt.isEmpty()
                    ? contentHashOpt
                    : calculateFileHash(file);
            
            // 检查文件是否已存在（基于内容哈希）
            Optional<File> existingFile = fileRepository.findByUserIdAndContentHash(userId, contentHash);
            if (existingFile.isPresent()) {
                log.info("文件已存在，跳过上传，文件ID: {}", existingFile.get().getFileId());
                throw new com.clouddisk.exception.BusinessException("文件重复", 409);
            }
            
            // 生成S3 Key
            String fileName = file.getOriginalFilename();
            String s3Key = generateS3Key(userId, fileName);
            
            // 上传到S3
            String s3Url = s3Service.uploadFile(file, s3Key);
            
            // 保存文件信息到数据库
            File fileEntity = new File(
                userId,
                fileName,
                filePath != null ? filePath : "/",
                s3Key,
                file.getSize(),
                contentHash
            );
            
            fileEntity = fileRepository.save(fileEntity);
            
            log.info("文件上传成功，文件ID: {}", fileEntity.getFileId());
            
            return new FileUploadResponse(
                fileEntity.getFileId(),
                fileEntity.getName(),
                fileEntity.getFileSize(),
                fileEntity.getFilePath()
            );
            
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除文件
     */
    @Transactional
    public void deleteFile(UUID userId, UUID fileId) {
        log.info("删除文件，用户ID: {}, 文件ID: {}", userId, fileId);
        
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        // 验证文件所有权
        if (!file.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此文件");
        }
        
        // 从S3删除文件
        s3Service.deleteFile(file.getS3Key());
        
        // 从数据库删除记录
        fileRepository.delete(file);
        
        log.info("文件删除成功，文件ID: {}", fileId);
    }
    
    /**
     * 下载文件
     */
    public byte[] downloadFile(UUID userId, UUID fileId) {
        log.info("下载文件，用户ID: {}, 文件ID: {}", userId, fileId);
        
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        // 验证文件所有权
        if (!file.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问此文件");
        }
        
        return s3Service.downloadFile(file.getS3Key());
    }
    
    /**
     * 获取文件信息
     */
    public FileResponse getFileInfo(UUID userId, UUID fileId) {
        log.info("获取文件信息，用户ID: {}, 文件ID: {}", userId, fileId);
        
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        // 验证文件所有权
        if (!file.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问此文件");
        }
        
        return convertToFileResponse(file);
    }
    
    /**
     * 转换实体到响应DTO
     */
    private FileResponse convertToFileResponse(File file) {
        FileResponse response = new FileResponse();
        response.setFileId(file.getFileId());
        response.setUserId(file.getUserId());
        response.setName(file.getName());
        response.setFilePath(file.getFilePath());
        response.setS3Key(file.getS3Key());
        response.setFileSize(file.getFileSize());
        response.setContentHash(file.getContentHash());
        response.setCreatedAt(file.getCreatedAt());
        response.setUpdatedAt(file.getUpdatedAt());
        return response;
    }
    
    /**
     * 生成S3 Key
     */
    private String generateS3Key(UUID userId, String fileName) {
        return String.format("user-%s/%s-%s", 
                userId, 
                System.currentTimeMillis(), 
                fileName.replaceAll("[^a-zA-Z0-9.-]", "_"));
    }
    
    /**
     * 计算文件哈希值
     */
    private String calculateFileHash(MultipartFile file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = file.getBytes();
        byte[] hashBytes = digest.digest(fileBytes);
        
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}