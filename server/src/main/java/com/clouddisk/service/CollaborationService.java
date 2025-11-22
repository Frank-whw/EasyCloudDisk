package com.clouddisk.service;

import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.dto.ShareRequest;
import com.clouddisk.dto.ShareResponseDto;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.entity.SharePermission;
import com.clouddisk.entity.ShareResourceType;
import com.clouddisk.entity.SharedResource;
import com.clouddisk.entity.User;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.FileRepository;
import com.clouddisk.repository.SharedResourceRepository;
import com.clouddisk.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 协同/共享能力服务。
 */
@Service
@Slf4j
public class CollaborationService {

    private final SharedResourceRepository sharedResourceRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;

    public CollaborationService(SharedResourceRepository sharedResourceRepository,
                                FileRepository fileRepository,
                                UserRepository userRepository) {
        this.sharedResourceRepository = sharedResourceRepository;
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
    }

    /**
     * 判断当前用户是否对指定文件拥有所需权限。
     */
    public FileEntity requireFileAccess(String fileId, String userId, SharePermission required) {
        FileEntity owned = fileRepository.findByFileIdAndUserId(fileId, userId).orElse(null);
        if (owned != null) {
            return owned;
        }

        FileEntity target = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        if (hasSharePermission(target, userId, required)) {
            return target;
        }

        throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前用户无权访问该文件");
    }

    /**
     * 将文件/目录共享给目标用户。
     */
    @Transactional
    public ShareResponseDto createShare(String ownerId, String fileId, ShareRequest request) {
        log.info("开始创建共享: ownerId={}, fileId={}, targetEmail={}, permission={}", 
                ownerId, fileId, request.getTargetEmail(), request.getPermission());
        
        FileEntity file = fileRepository.findByFileIdAndUserId(fileId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED, "只能共享自己拥有的文件/目录"));

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String normalizedEmail = request.getTargetEmail().trim();
        User target = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "目标用户不存在"));

        if (owner.getUserId().equals(target.getUserId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能共享给自己");
        }

        SharedResource sharedResource = new SharedResource();
        sharedResource.setOwnerId(ownerId);
        sharedResource.setTargetUserId(target.getUserId());
        sharedResource.setFileId(fileId);
        sharedResource.setPermission(request.getPermission());
        sharedResource.setResourceType(file.isDirectory() ? ShareResourceType.DIRECTORY : ShareResourceType.FILE);
        sharedResource.setIncludeSubtree(file.isDirectory());
        sharedResource.setResourcePath(buildFullPath(file));
        sharedResource.setExpiresAt(request.getExpiresAt());

        sharedResourceRepository.save(sharedResource);
        
        log.info("共享创建成功: shareId={}", sharedResource.getShareId());
        
        return toResponseDto(sharedResource, owner.getEmail(), target.getEmail());
    }

    /**
     * 列出当前文件的所有共享。
     */
    @Transactional(readOnly = true)
    public List<ShareResponseDto> listShares(String ownerId, String fileId) {
        FileEntity file = fileRepository.findByFileIdAndUserId(fileId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED, "只能查看自己文件的共享"));

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Map<String, String> userEmailMap = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getUserId, User::getEmail));

        return sharedResourceRepository.findAllByFileId(file.getFileId()).stream()
                .map(share -> toResponseDto(share, owner.getEmail(), userEmailMap.get(share.getTargetUserId())))
                .collect(Collectors.toList());
    }

    /**
     * 撤销共享。
     */
    @Transactional
    public void revokeShare(String ownerId, String shareId) {
        SharedResource share = sharedResourceRepository.findByShareIdAndOwnerId(shareId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));
        sharedResourceRepository.delete(share);
    }

    /**
     * 删除与文件相关的所有共享。
     */
    @Transactional
    public void removeAllSharesForFile(String fileId) {
        List<SharedResource> shares = sharedResourceRepository.findAllByFileId(fileId);
        if (!shares.isEmpty()) {
            sharedResourceRepository.deleteAll(shares);
        }
    }

    /**
     * 获取当前用户可访问的共享资源列表。
     */
    @Transactional(readOnly = true)
    public List<FileMetadataDto> listSharedWithMe(String userId) {
        Instant now = Instant.now();
        Map<String, String> userEmailMap = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getUserId, User::getEmail));

        return sharedResourceRepository.findAllByTargetUserId(userId).stream()
                .filter(share -> share.getExpiresAt() == null || !share.getExpiresAt().isBefore(now))
                .map(share -> Map.entry(share, fileRepository.findById(share.getFileId()).orElse(null)))
                .filter(entry -> entry.getValue() != null)
                .map(entry -> {
                    SharedResource share = entry.getKey();
                    FileEntity file = entry.getValue();
                    FileMetadataDto dto = toDto(file);
                    dto.setShared(true);
                    // 将 SharePermission 转换为 FileShare.SharePermission
                    dto.setPermission(convertToFileSharePermission(share.getPermission()));
                    dto.setOwnerEmail(userEmailMap.get(file.getUserId()));
                    dto.setShareId(share.getShareId());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private boolean hasSharePermission(FileEntity target, String userId, SharePermission required) {
        Instant now = Instant.now();
        List<SharedResource> shares = sharedResourceRepository.findAllByTargetUserId(userId);
        if (shares.isEmpty()) {
            return false;
        }
        String fullPath = buildFullPath(target);
        for (SharedResource share : shares) {
            if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(now)) {
                continue;
            }
            if (!share.getPermission().allows(required)) {
                continue;
            }
            if (share.getResourceType() == ShareResourceType.FILE &&
                    share.getFileId().equals(target.getFileId())) {
                return true;
            }
            if (share.getResourceType() == ShareResourceType.DIRECTORY && share.isIncludeSubtree()) {
                String sharedPath = share.getResourcePath();
                if (!sharedPath.endsWith("/")) {
                    sharedPath = sharedPath + "/";
                }
                String candidatePath = fullPath.endsWith("/") ? fullPath : fullPath + "/";
                if (candidatePath.startsWith(sharedPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    private ShareResponseDto toResponseDto(SharedResource sharedResource, String ownerEmail, String targetEmail) {
        ShareResponseDto dto = new ShareResponseDto();
        dto.setShareId(sharedResource.getShareId());
        dto.setFileId(sharedResource.getFileId());
        FileEntity file = fileRepository.findById(sharedResource.getFileId()).orElse(null);
        if (file != null) {
            dto.setFileName(file.getName());
        }
        dto.setResourceType(sharedResource.getResourceType());
        dto.setPermission(sharedResource.getPermission());
        dto.setOwnerEmail(ownerEmail);
        dto.setTargetEmail(targetEmail);
        dto.setCreatedAt(sharedResource.getCreatedAt());
        dto.setExpiresAt(sharedResource.getExpiresAt());
        dto.setIncludeSubtree(sharedResource.isIncludeSubtree());
        return dto;
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
        dto.setShared(false);
        return dto;
    }

    private String buildFullPath(FileEntity entity) {
        String parent = entity.getDirectoryPath();
        StringBuilder builder = new StringBuilder();
        if (!StringUtils.hasText(parent) || "/".equals(parent)) {
            builder.append("/");
        } else {
            builder.append(parent);
            if (!parent.endsWith("/")) {
                builder.append("/");
            }
        }
        builder.append(entity.getName());
        if (entity.isDirectory()) {
            builder.append("/");
        }
        return builder.toString().replaceAll("//+", "/");
    }

    /**
     * 将 SharePermission 转换为 FileShare.SharePermission
     */
    private com.clouddisk.entity.FileShare.SharePermission convertToFileSharePermission(SharePermission permission) {
        switch (permission) {
            case READ:
                return com.clouddisk.entity.FileShare.SharePermission.READ_ONLY;
            case WRITE:
                return com.clouddisk.entity.FileShare.SharePermission.READ_WRITE;
            default:
                return com.clouddisk.entity.FileShare.SharePermission.READ_ONLY;
        }
    }
}

