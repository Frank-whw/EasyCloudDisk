package com.clouddisk.dto;

import com.clouddisk.entity.SharePermission;
import com.clouddisk.entity.ShareResourceType;
import lombok.Data;

import java.time.Instant;

/**
 * 共享资源响应体。
 */
@Data
public class ShareResponseDto {
    private String shareId;
    private String fileId;
    private String fileName;
    private ShareResourceType resourceType;
    private SharePermission permission;
    private String ownerEmail;
    private String targetEmail;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean includeSubtree;
}

