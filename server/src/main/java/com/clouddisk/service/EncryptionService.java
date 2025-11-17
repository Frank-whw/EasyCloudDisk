package com.clouddisk.service;

import com.clouddisk.dto.EncryptedUploadRequest;
import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.entity.FileEncryptionMetadata;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.FileEncryptionMetadataRepository;
import com.clouddisk.repository.FileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 加密服务：管理客户端加密文件的元数据
 */
@Slf4j
@Service
public class EncryptionService {
    
    private final FileRepository fileRepository;
    private final FileEncryptionMetadataRepository encryptionMetadataRepository;
    private final FileService fileService;
    
    public EncryptionService(
            FileRepository fileRepository,
            FileEncryptionMetadataRepository encryptionMetadataRepository,
            FileService fileService) {
        this.fileRepository = fileRepository;
        this.encryptionMetadataRepository = encryptionMetadataRepository;
        this.fileService = fileService;
    }
    
    /**
     * 上传加密文件（客户端已加密）
     */
    @Transactional
    public FileMetadataDto uploadEncryptedFile(
            MultipartFile encryptedFile,
            EncryptedUploadRequest request,
            String userId) {
        
        // 验证加密参数
        validateEncryptionParams(request);
        
        // 使用标准上传流程（加密文件作为普通文件上传）
        FileMetadataDto metadata = fileService.upload(encryptedFile, request.getPath(), userId);
        
        // 保存加密元数据
        FileEncryptionMetadata encryptionMetadata = new FileEncryptionMetadata();
        encryptionMetadata.setFileId(metadata.getFileId());
        encryptionMetadata.setAlgorithm(request.getAlgorithm());
        encryptionMetadata.setKeyDerivation(request.getKeyDerivation());
        encryptionMetadata.setSalt(request.getSalt());
        encryptionMetadata.setIterations(request.getIterations());
        encryptionMetadata.setIv(request.getIv());
        encryptionMetadata.setConvergent(request.getConvergent() != null && request.getConvergent());
        encryptionMetadata.setOriginalSize(request.getOriginalSize());
        encryptionMetadata.setEncryptedSize(request.getEncryptedSize());
        encryptionMetadata.setOriginalHash(request.getOriginalHash());
        encryptionMetadata.setClientEncrypted(true);
        
        encryptionMetadataRepository.save(encryptionMetadata);
        
        log.info("加密文件上传成功: fileId={}, algorithm={}, convergent={}", 
                metadata.getFileId(), request.getAlgorithm(), request.getConvergent());
        
        return metadata;
    }
    
    /**
     * 获取文件的加密元数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getEncryptionMetadata(String fileId, String userId) {
        FileEntity file = fileRepository.findByFileIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        
        FileEncryptionMetadata metadata = encryptionMetadataRepository.findByFileId(fileId)
                .orElse(null);
        
        Map<String, Object> result = new HashMap<>();
        result.put("fileId", fileId);
        result.put("encrypted", metadata != null);
        
        if (metadata != null) {
            result.put("algorithm", metadata.getAlgorithm());
            result.put("keyDerivation", metadata.getKeyDerivation());
            result.put("salt", metadata.getSalt());
            result.put("iterations", metadata.getIterations());
            result.put("iv", metadata.getIv());
            result.put("convergent", metadata.getConvergent());
            result.put("originalSize", metadata.getOriginalSize());
            result.put("encryptedSize", metadata.getEncryptedSize());
            result.put("originalHash", metadata.getOriginalHash());
            result.put("clientEncrypted", metadata.getClientEncrypted());
        }
        
        return result;
    }
    
    /**
     * 检查是否支持收敛加密的秒传
     */
    @Transactional(readOnly = true)
    public boolean checkConvergentQuickUpload(String originalHash, String userId) {
        // 查找具有相同原始哈希的收敛加密文件
        return fileRepository.findAll().stream()
                .anyMatch(file -> {
                    FileEncryptionMetadata meta = encryptionMetadataRepository.findByFileId(file.getFileId())
                            .orElse(null);
                    return meta != null 
                            && meta.getConvergent() 
                            && originalHash.equals(meta.getOriginalHash());
                });
    }
    
    /**
     * 验证加密参数
     */
    private void validateEncryptionParams(EncryptedUploadRequest request) {
        if (request.getAlgorithm() == null || request.getAlgorithm().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "加密算法不能为空");
        }
        
        // 验证支持的加密算法
        if (!request.getAlgorithm().matches("AES-256-(GCM|CBC)")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, 
                    "不支持的加密算法，仅支持: AES-256-GCM, AES-256-CBC");
        }
        
        // 如果使用收敛加密，必须提供原始哈希
        if (Boolean.TRUE.equals(request.getConvergent()) && 
            (request.getOriginalHash() == null || request.getOriginalHash().isEmpty())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, 
                    "收敛加密需要提供原始文件哈希");
        }
        
        log.debug("加密参数验证通过: algorithm={}, convergent={}", 
                request.getAlgorithm(), request.getConvergent());
    }
}
