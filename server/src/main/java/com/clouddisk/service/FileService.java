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
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FileService {

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public FileService(FileRepository fileRepository,
                       FileVersionRepository fileVersionRepository,
                       UserRepository userRepository,
                       StorageService storageService) {
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<FileMetadataDto> listFiles(String userId) {
        return fileRepository.findAllByUser_UserId(userId).stream()
                .sorted(Comparator.comparing(FileEntity::isDirectory).reversed()
                        .thenComparing(FileEntity::getDirectoryPath)
                        .thenComparing(FileEntity::getName))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public FileMetadataDto upload(MultipartFile file, String directoryPath, String userId) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件不能为空");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String normalizedPath = normalizePath(directoryPath);
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件名不能为空");
        }

        fileRepository.findByUser_UserIdAndDirectoryPathAndName(userId, normalizedPath, fileName)
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

        Optional<FileEntity> duplicate = fileRepository.findFirstByContentHash(hash);
        String storageKey;
        String keyPrefix = (userId + normalizedPath).replaceAll("/+", "/");
        if (duplicate.isPresent() && duplicate.get().getStorageKey() != null) {
            storageKey = duplicate.get().getStorageKey();
        } else {
            storageKey = storageService.storeFile(file, keyPrefix, true);
        }

        FileEntity entity = fileRepository.findByUser_UserIdAndDirectoryPathAndName(userId, normalizedPath, fileName)
                .orElse(null);

        if (entity == null) {
            entity = new FileEntity();
            entity.setUser(user);
            entity.setDirectory(false);
            entity.setDirectoryPath(normalizedPath);
            entity.setName(fileName);
            entity.setVersion(1);
        } else {
            FileVersion version = new FileVersion();
            version.setFile(entity);
            version.setVersionNumber(entity.getVersion());
            version.setStorageKey(entity.getStorageKey());
            version.setFileSize(entity.getFileSize());
            version.setContentHash(entity.getContentHash());
            fileVersionRepository.save(version);
            entity.setVersion(entity.getVersion() + 1);
        }

        entity.setStorageKey(storageKey);
        entity.setFileSize(bytes.length);
        entity.setContentHash(hash);
        fileRepository.save(entity);

        FileVersion latest = new FileVersion();
        latest.setFile(entity);
        latest.setVersionNumber(entity.getVersion());
        latest.setStorageKey(storageKey);
        latest.setFileSize(bytes.length);
        latest.setContentHash(hash);
        fileVersionRepository.save(latest);

        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(String fileId, String userId) {
        FileEntity file = fileRepository.findByFileIdAndUser_UserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        if (file.isDirectory()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "目录无法下载");
        }
        InputStream stream = storageService.loadFile(file.getStorageKey(), true);
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

    @Transactional
    public void delete(String fileId, String userId) {
        FileEntity file = fileRepository.findByFileIdAndUser_UserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        if (!file.isDirectory() && file.getStorageKey() != null) {
            boolean shared = fileRepository.findAll().stream()
                    .anyMatch(other -> !other.getFileId().equals(file.getFileId()) && file.getStorageKey().equals(other.getStorageKey()));
            if (!shared) {
                storageService.deleteFile(file.getStorageKey());
            }
        }
        fileVersionRepository.deleteAll(fileVersionRepository.findAllByFileOrderByVersionNumberDesc(file));
        fileRepository.delete(file);
    }

    @Transactional
    public FileMetadataDto createDirectory(String directoryPath, String name, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String normalizedParent = normalizePath(directoryPath);
        String safeName = name.trim();
        if (!StringUtils.hasText(safeName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "目录名不能为空");
        }
        fileRepository.findByUser_UserIdAndDirectoryPathAndName(userId, normalizedParent, safeName)
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.DIRECTORY_ALREADY_EXISTS);
                });
        FileEntity entity = new FileEntity();
        entity.setUser(user);
        entity.setDirectory(true);
        entity.setDirectoryPath(normalizedParent);
        entity.setName(safeName);
        entity.setVersion(1);
        entity.setFileSize(0);
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

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String normalized = path.replace("\\", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
