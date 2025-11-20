package com.clouddisk.service;

import com.clouddisk.dto.CreateShareRequest;
import com.clouddisk.dto.FileShareDto;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.entity.FileShare;
import com.clouddisk.entity.User;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.FileRepository;
import com.clouddisk.repository.FileShareRepository;
import com.clouddisk.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FileShareService {

    private final FileShareRepository fileShareRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public FileShareService(FileShareRepository fileShareRepository,
                           FileRepository fileRepository,
                           UserRepository userRepository,
                           FileService fileService,
                           PasswordEncoder passwordEncoder) {
        this.fileShareRepository = fileShareRepository;
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 创建文件共享
     */
    @Transactional
    public FileShareDto createShare(String userId, CreateShareRequest request) {
        // 验证文件存在且用户有权限
        FileEntity file = fileRepository.findByFileIdAndUserId(request.getFileId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        // 检查是否已经存在共享
        Optional<FileShare> existingShare = fileShareRepository.findByFileIdAndOwnerIdAndActiveTrue(
                request.getFileId(), userId);
        if (existingShare.isPresent()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件已经被共享");
        }

        FileShare share = new FileShare();
        share.setFileId(request.getFileId());
        share.setOwnerId(userId);
        share.setShareName(request.getShareName());
        share.setShareDescription(request.getShareDescription());
        share.setPermission(request.getPermission());
        share.setExpiresAt(request.getExpiresAt());
        share.setMaxDownloads(request.getMaxDownloads());

        // 如果设置了密码，进行加密
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            share.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        fileShareRepository.save(share);
        log.info("用户 {} 创建了文件 {} 的共享: {}", userId, request.getFileId(), share.getShareId());

        return toDto(share, file);
    }

    /**
     * 获取用户的所有共享
     */
    @Transactional(readOnly = true)
    public List<FileShareDto> listUserShares(String userId) {
        List<FileShare> shares = fileShareRepository.findAllByOwnerIdAndActiveTrue(userId);
        return shares.stream()
                .map(share -> {
                    FileEntity file = fileRepository.findById(share.getFileId()).orElse(null);
                    return toDto(share, file);
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取文件的共享信息
     */
    @Transactional(readOnly = true)
    public Optional<FileShareDto> getFileShare(String fileId, String userId) {
        Optional<FileShare> share = fileShareRepository.findByFileIdAndOwnerIdAndActiveTrue(fileId, userId);
        if (share.isPresent()) {
            FileEntity file = fileRepository.findById(fileId).orElse(null);
            return Optional.of(toDto(share.get(), file));
        }
        return Optional.empty();
    }

    /**
     * 通过共享ID访问文件（公开访问）
     */
    @Transactional(readOnly = true)
    public FileShareDto getShareInfo(String shareId) {
        FileShare share = fileShareRepository.findActiveShare(shareId, Instant.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND, "共享不存在或已过期"));

        FileEntity file = fileRepository.findById(share.getFileId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        return toDto(share, file);
    }

    /**
     * 验证共享访问密码
     */
    @Transactional(readOnly = true)
    public boolean validateSharePassword(String shareId, String password) {
        FileShare share = fileShareRepository.findActiveShare(shareId, Instant.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));

        if (share.getPassword() == null) {
            return true; // 无密码保护
        }

        return passwordEncoder.matches(password, share.getPassword());
    }

    /**
     * 下载共享文件
     */
    @Transactional
    public ResponseEntity<Resource> downloadSharedFile(String shareId, String password) {
        FileShare share = fileShareRepository.findActiveShare(shareId, Instant.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND, "共享不存在或已过期"));

        // 验证密码
        if (share.getPassword() != null && !passwordEncoder.matches(password, share.getPassword())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "密码错误");
        }

        // 检查权限
        if (share.getPermission() == FileShare.SharePermission.READ_WRITE) {
            // READ_WRITE 权限也允许下载
        } else if (share.getPermission() != FileShare.SharePermission.DOWNLOAD_ONLY &&
                   share.getPermission() != FileShare.SharePermission.READ_ONLY) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无下载权限");
        }

        FileEntity file = fileRepository.findById(share.getFileId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        // 增加下载计数
        share.setDownloadCount(share.getDownloadCount() + 1);
        fileShareRepository.save(share);

        log.info("共享文件下载: shareId={}, fileId={}, downloadCount={}", 
                shareId, file.getFileId(), share.getDownloadCount());

        // 调用文件服务下载文件
        return fileService.download(file.getFileId(), share.getOwnerId());
    }

    /**
     * 取消文件共享
     */
    @Transactional
    public void cancelShare(String shareId, String userId) {
        FileShare share = fileShareRepository.findById(shareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));

        if (!share.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有共享创建者可以取消共享");
        }

        share.setActive(false);
        fileShareRepository.save(share);
        log.info("用户 {} 取消了共享: {}", userId, shareId);
    }

    /**
     * 清理过期的共享
     */
    @Transactional
    public void cleanupExpiredShares() {
        List<FileShare> expiredShares = fileShareRepository.findExpiredShares(Instant.now());
        List<FileShare> maxDownloadShares = fileShareRepository.findMaxDownloadReachedShares();

        expiredShares.forEach(share -> share.setActive(false));
        maxDownloadShares.forEach(share -> share.setActive(false));

        fileShareRepository.saveAll(expiredShares);
        fileShareRepository.saveAll(maxDownloadShares);

        log.info("清理过期共享: {} 个过期, {} 个达到最大下载次数", 
                expiredShares.size(), maxDownloadShares.size());
    }

    private FileShareDto toDto(FileShare share, FileEntity file) {
        FileShareDto dto = new FileShareDto();
        dto.setShareId(share.getShareId());
        dto.setFileId(share.getFileId());
        dto.setOwnerId(share.getOwnerId());
        dto.setShareName(share.getShareName());
        dto.setShareDescription(share.getShareDescription());
        dto.setPermission(share.getPermission());
        dto.setHasPassword(share.getPassword() != null);
        dto.setExpiresAt(share.getExpiresAt());
        dto.setDownloadCount(share.getDownloadCount());
        dto.setMaxDownloads(share.getMaxDownloads());
        dto.setActive(share.isActive());
        dto.setCreatedAt(share.getCreatedAt());
        dto.setShareUrl(baseUrl + "/api/shares/" + share.getShareId());

        if (file != null) {
            dto.setFileName(file.getName());
        }

        // 获取所有者邮箱
        userRepository.findById(share.getOwnerId())
                .ifPresent(user -> dto.setOwnerEmail(user.getEmail()));

        return dto;
    }
}
