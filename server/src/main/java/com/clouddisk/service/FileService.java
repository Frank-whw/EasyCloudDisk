package com.clouddisk.service;

import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.entity.FileShare;
import com.clouddisk.entity.FileVersion;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ConflictException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.FileChunkMappingRepository;
import com.clouddisk.repository.FileRepository;
import com.clouddisk.repository.FileShareRepository;
import com.clouddisk.repository.FileVersionRepository;
import com.clouddisk.repository.UserRepository;
import com.clouddisk.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件业务逻辑服务，处理上传、下载、删除以及目录操作。
 */
@Service
@Slf4j
public class FileService {

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final ChunkService chunkService;
    private final FileShareRepository fileShareRepository;
    private final FileChunkMappingRepository fileChunkMappingRepository;

    public FileService(FileRepository fileRepository,
                       FileVersionRepository fileVersionRepository,
                       UserRepository userRepository,
                       StorageService storageService,
                       ChunkService chunkService,
                       FileShareRepository fileShareRepository,
                       FileChunkMappingRepository fileChunkMappingRepository) {
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.chunkService = chunkService;
        this.fileShareRepository = fileShareRepository;
        this.fileChunkMappingRepository = fileChunkMappingRepository;
    }

    /**
     * 列出用户文件。
     * @param userId 用户ID
     * @param path 可选参数，指定要查询的目录路径，如果为null或空字符串，则返回根目录下的文件
     */
    @Transactional(readOnly = true)
    public List<FileMetadataDto> listFiles(String userId, String path) {
        String normalizedPath = normalizePath(path != null ? path : "/");
        
        return fileRepository.findAllByUserId(userId).stream()
                .filter(file -> {
                    // 过滤出指定路径下的文件和目录
                    return file.getDirectoryPath().equals(normalizedPath);
                })
                .sorted(Comparator.comparing(FileEntity::isDirectory).reversed()
                        .thenComparing(FileEntity::getName))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 上传文件并维护版本记录(使用块级去重存储)。
     */
    @Transactional
    public FileMetadataDto upload(MultipartFile file, String directoryPath, String userId) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件不能为空");
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String normalizedPath = normalizePath(directoryPath);
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件名不能为空");
        }

        fileRepository.findByUserIdAndDirectoryPathAndName(userId, normalizedPath, fileName)
                .filter(FileEntity::isDirectory)
                .ifPresent(existingDir -> {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "同名目录已存在");
                });

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取文件失败", ex);
        }
        String hash = DigestUtils.sha256Hex(bytes);

        FileEntity entity = fileRepository.findByUserIdAndDirectoryPathAndName(userId, normalizedPath, fileName)
                .orElse(null);

        boolean isNewFile = (entity == null);
        if (isNewFile) {
            entity = new FileEntity();
            entity.setUserId(userId);
            entity.setDirectory(false);
            entity.setDirectoryPath(normalizedPath);
            entity.setName(fileName);
            entity.setVersion(1);
        } else {
            // 保存旧版本到历史
            FileVersion version = new FileVersion();
            version.setFileId(entity.getFileId());
            version.setVersionNumber(entity.getVersion());
            version.setStorageKey(entity.getStorageKey());
            version.setFileSize(entity.getFileSize());
            version.setContentHash(entity.getContentHash());
            fileVersionRepository.save(version);
            
            log.info("文件版本更新: fileId={}, 旧版本={}, 准备删除旧块映射", entity.getFileId(), entity.getVersion());
            
            // 检查是否存在旧版本的块映射
            List<?> existingMappings = fileChunkMappingRepository.findByFileIdAndVersionNumberOrderBySequenceNumber(entity.getFileId(), entity.getVersion());
            log.info("找到 {} 个旧版本块映射记录", existingMappings.size());
            
            // 删除当前版本的块映射，为新版本让路
            try {
                fileChunkMappingRepository.deleteByFileIdAndVersionNumber(entity.getFileId(), entity.getVersion());
                log.info("已删除旧版本的块映射: fileId={}, version={}", entity.getFileId(), entity.getVersion());
            } catch (Exception e) {
                log.error("删除旧版本块映射失败: fileId={}, version={}, error={}", entity.getFileId(), entity.getVersion(), e.getMessage(), e);
                throw e;
            }
            
            entity.setVersion(entity.getVersion() + 1);
            log.info("文件版本更新为: {}", entity.getVersion());
        }

        entity.setStorageKey("chunked"); // 标记为分块存储
        entity.setFileSize((long) bytes.length);
        entity.setContentHash(hash);
        
        try {
            fileRepository.save(entity);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException(
                "文件正在被其他用户修改，请刷新后重试",
                "CONCURRENT_MODIFICATION",
                "乐观锁冲突"
            );
        }

        // 使用块级存储(自动去重+压缩)
        chunkService.storeFileInChunks(
                entity.getFileId(), 
                entity.getVersion(), 
                bytes, 
                userId, 
                true
        );

        // 保存当前版本信息
        FileVersion latest = new FileVersion();
        latest.setFileId(entity.getFileId());
        latest.setVersionNumber(entity.getVersion());
        latest.setStorageKey("chunked");
        latest.setFileSize((long) bytes.length);
        latest.setContentHash(hash);
        fileVersionRepository.save(latest);
        
        log.info("文件上传完成: fileId={}, version={}, isNewFile={}, userId={}", 
                entity.getFileId(), entity.getVersion(), isNewFile, userId);

        return toDto(entity);
    }

    /**
     * 下载文件(支持块级存储)。
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(String fileId, String userId) {
        FileEntity file = fileRepository.findByFileIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        if (file.isDirectory()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "目录无法下载");
        }
        
        InputStream stream;
        
        if ("chunked".equals(file.getStorageKey()) || file.getStorageKey() == null) {
            // 从块重组文件 (包括旧文件没有设置storageKey的情况)
            try {
                stream = chunkService.assembleFile(file.getFileId(), file.getVersion());
            } catch (BusinessException e) {
                // 如果块映射不存在，可能是数据不一致，尝试从OSS直接下载
                if (e.getMessage().contains("文件块映射不存在")) {
                    log.warn("文件 {} 标记为chunked但块映射不存在，尝试从OSS下载", fileId);
                    // 尝试从FileVersion中查找是否有有效的storageKey
                    FileVersion latestVersion = fileVersionRepository.findAllByFileIdOrderByVersionNumberDesc(fileId)
                        .stream()
                        .filter(v -> v.getStorageKey() != null && !"chunked".equals(v.getStorageKey()))
                        .findFirst()
                        .orElse(null);
                    
                    if (latestVersion != null) {
                        log.info("从版本记录中找到有效的storageKey: {}", latestVersion.getStorageKey());
                        stream = storageService.loadFile(latestVersion.getStorageKey(), true);
                    } else {
                        // 如果也没有storageKey，抛出错误
                        throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件数据不存在，请重新上传");
                    }
                } else {
                    throw e;
                }
            }
        } else {
            // 旧格式:直接从存储加载
            stream = storageService.loadFile(file.getStorageKey(), true);
        }
        
        Resource resource = new InputStreamResource(stream);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(file.getName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .headers(headers -> headers.setContentDisposition(contentDisposition))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * 删除文件或目录(支持块级存储)。
     */
    @Transactional
    public void delete(String fileId, String userId) {
        FileEntity file = fileRepository.findByFileIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        
        if (!file.isDirectory() && file.getStorageKey() != null) {
            if ("chunked".equals(file.getStorageKey())) {
                // 删除块存储(自动处理引用计数)
                chunkService.deleteFileChunks(file.getFileId());
            } else {
                // 旧格式:检查共享后删除
                boolean shared = fileRepository.findAll().stream()
                        .anyMatch(other -> !other.getFileId().equals(file.getFileId()) && file.getStorageKey().equals(other.getStorageKey()));
                if (!shared) {
                    storageService.deleteFile(file.getStorageKey());
                }
            }
        }
        
        fileVersionRepository.deleteAll(fileVersionRepository.findAllByFileIdOrderByVersionNumberDesc(file.getFileId()));
        fileRepository.delete(file);
    }

    /**
     * 创建目录。
     */
    @Transactional
    public FileMetadataDto createDirectory(String directoryPath, String name, String userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String normalizedParent = normalizePath(directoryPath);
        String safeName = name.trim();
        if (!StringUtils.hasText(safeName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "目录名不能为空");
        }
        if (safeName.contains("/") || safeName.contains("\\") || safeName.contains("..")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法目录名");
        }
        fileRepository.findByUserIdAndDirectoryPathAndName(userId, normalizedParent, safeName)
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.DIRECTORY_ALREADY_EXISTS);
                });
        FileEntity entity = new FileEntity();
        entity.setUserId(userId);
        entity.setDirectory(true);
        entity.setDirectoryPath(normalizedParent);
        entity.setName(safeName);
        entity.setVersion(1);
        entity.setFileSize(0L);
        fileRepository.save(entity);
        return toDto(entity);
    }

    private FileMetadataDto toDto(FileEntity entity) {
        FileMetadataDto dto = new FileMetadataDto();
        dto.setFileId(entity.getFileId());
        dto.setName(entity.getName());
        dto.setPath(entity.getDirectoryPath() + "/" + entity.getName());
        dto.setSize(entity.getFileSize() != null ? entity.getFileSize() : 0);
        dto.setDirectory(entity.isDirectory());
        dto.setHash(entity.getContentHash());
        dto.setVersion(entity.getVersion());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // 检查是否有共享
        fileShareRepository.findByFileIdAndOwnerIdAndActiveTrue(entity.getFileId(), entity.getUserId())
                .ifPresentOrElse(share -> {
                    dto.setShared(true);
                    dto.setShareId(share.getShareId());
                    dto.setPermission(share.getPermission());
                    dto.setShareUrl("/api/shares/" + share.getShareId());
                    dto.setHasSharePassword(share.getPassword() != null);
                    dto.setShareExpiresAt(share.getExpiresAt());
                }, () -> dto.setShared(false));

        return dto;
    }

    /**
     * 标准化目录路径，统一斜杠并确保以根路径开头。
     */
    public String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String s = path.replace("\\", "/");
        String[] parts = s.split("/");
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        for (String part : parts) {
            if (part == null || part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!stack.isEmpty()) {
                    stack.pollLast();
                }
                continue;
            }
            stack.addLast(part);
        }
        String result = "/" + String.join("/", stack);
        if (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? "/" : result;
    }

}