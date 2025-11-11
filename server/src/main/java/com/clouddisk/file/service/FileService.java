package com.clouddisk.file.service;

import com.clouddisk.common.dto.FileResponse;
import com.clouddisk.common.dto.FileUploadResponse;
import com.clouddisk.common.exception.BusinessException;
import com.clouddisk.file.entity.File;
import com.clouddisk.file.repository.FileRepository;
import com.clouddisk.storage.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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
    private static final long MAX_UPLOAD_SIZE_BYTES = 100L * 1024 * 1024; // 100MB 与 application.yml 保持一致
    
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
     * 上传文件（支持秒传）
     */
    @Transactional
    public FileUploadResponse uploadFile(UUID userId, MultipartFile file, String filePath, String contentHashOpt) {
        log.info("上传文件，用户ID: {}, 文件名: {}, 路径: {}", userId, file.getOriginalFilename(), filePath);
        
        try {
            // 服务端大小校验（避免绕过控制器限制）
            long size = file.getSize();
            if (size <= 0 || size > MAX_UPLOAD_SIZE_BYTES) {
                throw new BusinessException("文件大小非法或超过限制", 413);
            }
            // 使用客户端提供的内容哈希（如果有），否则计算
            String contentHash = contentHashOpt != null && !contentHashOpt.isEmpty()
                    ? contentHashOpt
                    : calculateFileHash(file);
            
            String fileName = sanitizeFileName(file.getOriginalFilename());
            String normalizedPath = normalizePath(filePath);
            
            // 检查用户是否已有相同文件（基于内容哈希）- 防止重复上传
            Optional<File> userExistingFile = fileRepository.findByUserIdAndContentHash(userId, contentHash);
            if (userExistingFile.isPresent()) {
                log.info("用户已有相同内容文件，拒绝重复上传，文件ID: {}", userExistingFile.get().getFileId());
                throw new BusinessException("文件重复", 409);
            }
            
            // 检查系统中是否已存在相同内容的文件（跨用户去重，秒传）
            Optional<File> globalExistingFile = fileRepository.findFirstByContentHash(contentHash);
            String s3Key;
            
            if (globalExistingFile.isPresent()) {
                // 秒传：复用已存在的 S3 对象
                s3Key = globalExistingFile.get().getS3Key();
                log.info("秒传成功！复用 S3 对象: {}", s3Key);
            } else {
                // 正常上传到 S3
                s3Key = generateS3Key(userId, fileName);
                s3Service.uploadFile(file, s3Key);
                log.info("文件上传到 S3 成功: {}", s3Key);
            }
            
            // 创建文件元数据记录
            File fileEntity = new File(
                userId,
                fileName,
                normalizedPath,
                s3Key,
                file.getSize(),
                contentHash
            );
            
            fileEntity = fileRepository.save(fileEntity);
            
            log.info("文件元数据保存成功，文件ID: {}", fileEntity.getFileId());
            
            return new FileUploadResponse(
                fileEntity.getFileId(),
                fileEntity.getName(),
                fileEntity.getFileSize(),
                fileEntity.getFilePath()
            );
            
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("文件上传失败", e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除文件（支持引用计数，避免误删共享 S3 对象）
     */
    @Transactional
    public void deleteFile(UUID userId, UUID fileId) {
        log.info("删除文件，用户ID: {}, 文件ID: {}", userId, fileId);
        
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("文件不存在", 404));
        
        // 验证文件所有权
        if (!file.getUserId().equals(userId)) {
            throw new BusinessException("无权删除此文件", 403);
        }
        
        String s3Key = file.getS3Key();
        
        // 先删除数据库记录
        fileRepository.delete(file);
        log.info("文件元数据已删除，文件ID: {}", fileId);
        
        // 检查 S3 对象是否还被其他文件引用
        long refCount = fileRepository.countByS3Key(s3Key);
        if (refCount == 0) {
            // 没有其他引用，可以安全删除 S3 对象
            s3Service.deleteFile(s3Key);
            log.info("S3 对象已删除: {}", s3Key);
        } else {
            log.info("S3 对象仍被 {} 个文件引用，保留: {}", refCount, s3Key);
        }
        
        log.info("文件删除完成，文件ID: {}", fileId);
    }
    
    /**
     * 下载文件
     */
    public byte[] downloadFile(UUID userId, UUID fileId) {
        log.info("下载文件，用户ID: {}, 文件ID: {}", userId, fileId);
        
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("文件不存在", 404));
        
        // 验证文件所有权
        if (!file.getUserId().equals(userId)) {
            throw new BusinessException("无权访问此文件", 403);
        }
        
        return s3Service.downloadFile(file.getS3Key());
    }

    /**
     * 以流式方式打开文件内容
     */
    public InputStream openFileStream(UUID userId, UUID fileId) {
        log.info("以流式方式打开文件，用户ID: {}, 文件ID: {}", userId, fileId);

        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("文件不存在", 404));

        // 验证文件所有权
        if (!file.getUserId().equals(userId)) {
            throw new BusinessException("无权访问此文件", 403);
        }

        return s3Service.downloadFileStream(file.getS3Key());
    }
    
    /**
     * 检查文件是否已存在（用于去重验证）
     */
    public boolean checkFileExists(UUID userId, String contentHash) {
        log.info("检查文件是否存在，用户ID: {}, 内容哈希: {}", userId, contentHash);
        
        // 检查用户是否已有相同文件（基于内容哈希）
        Optional<File> userExistingFile = fileRepository.findByUserIdAndContentHash(userId, contentHash);
        if (userExistingFile.isPresent()) {
            log.info("用户已有相同内容文件，文件ID: {}", userExistingFile.get().getFileId());
            return true; // 文件已存在
        }
        
        log.debug("文件不存在，可以上传 (哈希: {})", contentHash);
        return false; // 文件不存在
    }
    
    /**
     * 通知服务端上传完成（用于S3直接上传后的通知）
     */
    @Transactional
    public FileUploadResponse notifyUploadComplete(UUID userId, String contentHash, String filePath, Long fileSizeOpt) {
        log.info("通知上传完成，用户ID: {}, 内容哈希: {}, 文件路径: {}", userId, contentHash, filePath);
        
        try {
            if (fileSizeOpt != null && (fileSizeOpt <= 0 || fileSizeOpt > 100L * 1024 * 1024)) {
                throw new BusinessException("文件大小非法或超过限制", 413);
            }
            // 检查用户是否已有相同文件（防止重复通知）
            Optional<File> userExistingFile = fileRepository.findByUserIdAndContentHash(userId, contentHash);
            if (userExistingFile.isPresent()) {
                log.info("用户已有相同内容文件，忽略通知，文件ID: {}", userExistingFile.get().getFileId());
                return new FileUploadResponse(
                    userExistingFile.get().getFileId(),
                    userExistingFile.get().getName(),
                    userExistingFile.get().getFileSize(),
                    userExistingFile.get().getFilePath()
                );
            }
            
            // 检查系统中是否已存在相同内容的文件（跨用户去重）
            Optional<File> globalExistingFile = fileRepository.findFirstByContentHash(contentHash);
            String s3Key;
            String normalizedPath = normalizePath(filePath);
            String fileName = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
            fileName = sanitizeFileName(fileName);
            
            if (globalExistingFile.isPresent()) {
                // 秒传：复用已存在的 S3 对象
                s3Key = globalExistingFile.get().getS3Key();
                log.info("秒传成功！复用 S3 对象: {}", s3Key);
            } else {
                // S3直接上传的情况，需要生成新的S3 key
                s3Key = generateS3Key(userId, fileName);
                log.warn("S3直接上传后通知，但未找到已存在的S3对象，生成新key: {}", s3Key);
            }
            
            // 创建文件元数据记录
            long finalSize = globalExistingFile.map(File::getFileSize).orElse(fileSizeOpt != null ? fileSizeOpt : 0L);
            File fileEntity = new File(
                userId,
                fileName,
                normalizedPath,
                s3Key,
                finalSize,
                contentHash
            );
            
            fileEntity = fileRepository.save(fileEntity);
            
            log.info("文件元数据保存成功，文件ID: {}", fileEntity.getFileId());
            
            return new FileUploadResponse(
                fileEntity.getFileId(),
                fileEntity.getName(),
                fileEntity.getFileSize(),
                fileEntity.getFilePath()
            );
            
        } catch (Exception e) {
            log.error("通知上传完成失败", e);
            throw new BusinessException("通知上传完成失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取文件信息
     */
    public FileResponse getFileInfo(UUID userId, UUID fileId) {
        log.info("获取文件信息，用户ID: {}, 文件ID: {}", userId, fileId);
        
        File file = fileRepository.findByFileIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException("文件不存在", 404));
        
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
        // 使用流式读取避免一次性加载到内存
        try (java.io.InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder(hashBytes.length * 2);
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 规范化文件路径，防止路径遍历
     * 将分隔符统一为'/'，移除'.'段，安全处理'..'段，确保根相对路径
     */
    private String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return "/";
        }
        String path = rawPath.replace('\\', '/');
        String[] parts = path.split("/");
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        for (String part : parts) {
            if (part == null || part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                // 防止越过根路径
                if (!stack.isEmpty()) {
                    stack.pollLast();
                }
                continue;
            }
            // 仅保留安全字符，避免注入奇异字符
            String safe = part.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (!safe.isEmpty()) {
                stack.addLast(safe);
            }
        }
        StringBuilder normalized = new StringBuilder("/");
        java.util.Iterator<String> it = stack.iterator();
        while (it.hasNext()) {
            normalized.append(it.next());
            if (it.hasNext()) normalized.append('/');
        }
        String result = normalized.toString();
        // 限制最大长度，避免异常超长路径
        if (result.length() > 1024) {
            result = result.substring(0, 1024);
        }
        return result;
    }

    /**
     * 清理文件名，保留常见安全字符并限制长度
     */
    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "unnamed";
        }
        String cleaned = name.trim().replace('\\', '/');
        // 仅保留文件名部分，去掉可能的路径
        int idx = cleaned.lastIndexOf('/');
        if (idx >= 0) {
            cleaned = cleaned.substring(idx + 1);
        }
        // 替换不安全字符
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9._-]", "_");
        // 避免以点开头（隐藏文件）影响显示
        if (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            cleaned = "unnamed";
        }
        // 最长255字符
        if (cleaned.length() > 255) {
            cleaned = cleaned.substring(0, 255);
        }
        return cleaned;
    }
}