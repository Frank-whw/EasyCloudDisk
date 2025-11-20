package com.clouddisk.service;

import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.entity.FileVersion;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.FileRepository;
import com.clouddisk.repository.FileVersionRepository;
import com.clouddisk.repository.UserRepository;
import com.clouddisk.storage.StorageService;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
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
public class FileService {

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final ChunkService chunkService;

    public FileService(FileRepository fileRepository,
                       FileVersionRepository fileVersionRepository,
                       UserRepository userRepository,
                       StorageService storageService,
                       ChunkService chunkService) {
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.chunkService = chunkService;
    }

    /**
     * 列出用户文件。
     */
    @Transactional(readOnly = true)
    public List<FileMetadataDto> listFiles(String userId) {
        return fileRepository.findAllByUserId(userId).stream()
                .sorted(Comparator.comparing(FileEntity::isDirectory).reversed()
                        .thenComparing(FileEntity::getDirectoryPath)
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
            // 保存旧版本
            FileVersion version = new FileVersion();
            version.setFileId(entity.getFileId());
            version.setVersionNumber(entity.getVersion());
            version.setStorageKey(entity.getStorageKey());
            version.setFileSize(entity.getFileSize());
            version.setContentHash(entity.getContentHash());
            fileVersionRepository.save(version);
            entity.setVersion(entity.getVersion() + 1);
        }

        entity.setStorageKey("chunked"); // 标记为分块存储
        entity.setFileSize((long) bytes.length);
        entity.setContentHash(hash);
        fileRepository.save(entity);

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
        if ("chunked".equals(file.getStorageKey())) {
            // 从块重组文件
            stream = chunkService.assembleFile(file.getFileId(), file.getVersion());
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
                .contentLength(file.getFileSize())
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
        dto.setPath(entity.getDirectoryPath());
        dto.setSize(entity.getFileSize());
        dto.setDirectory(entity.isDirectory());
        dto.setHash(entity.getContentHash());
        dto.setVersion(entity.getVersion());
        dto.setUpdatedAt(entity.getUpdatedAt());
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