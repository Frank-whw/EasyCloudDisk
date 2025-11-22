package com.clouddisk.controller;

import com.clouddisk.dto.ApiResponse;
import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.dto.ShareRequest;
import com.clouddisk.dto.ShareResponseDto;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.security.UserPrincipal;
import com.clouddisk.service.CollaborationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件协同与共享接口。
 */
@RestController
@RequestMapping("/collaboration")
public class CollaborationController {

    private final CollaborationService collaborationService;

    public CollaborationController(CollaborationService collaborationService) {
        this.collaborationService = collaborationService;
    }

    /**
     * 创建文件/目录共享。
     */
    @PostMapping("/shares")
    public ResponseEntity<ApiResponse<ShareResponseDto>> createShare(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam String fileId,
            @Valid @RequestBody ShareRequest request) {
        ensureUser(user);
        ShareResponseDto response = collaborationService.createShare(user.getUserId(), fileId, request);
        return ResponseEntity.ok(ApiResponse.success("共享创建成功", ErrorCode.SUCCESS.name(), response));
    }

    /**
     * 获取指定文件的所有共享记录。
     */
    @GetMapping("/shares")
    public ResponseEntity<ApiResponse<List<ShareResponseDto>>> listShares(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam String fileId) {
        ensureUser(user);
        List<ShareResponseDto> shares = collaborationService.listShares(user.getUserId(), fileId);
        return ResponseEntity.ok(ApiResponse.success(shares));
    }

    /**
     * 撤销共享。
     */
    @DeleteMapping("/shares/{shareId}")
    public ResponseEntity<ApiResponse<Void>> revokeShare(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String shareId) {
        ensureUser(user);
        collaborationService.revokeShare(user.getUserId(), shareId);
        return ResponseEntity.ok(ApiResponse.success("共享已撤销", ErrorCode.SUCCESS.name(), null));
    }

    /**
     * 获取与我共享的文件列表。
     */
    @GetMapping("/shared-with-me")
    public ResponseEntity<ApiResponse<List<FileMetadataDto>>> listSharedWithMe(
            @AuthenticationPrincipal UserPrincipal user) {
        ensureUser(user);
        List<FileMetadataDto> files = collaborationService.listSharedWithMe(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(files));
    }

    /**
     * 校验当前请求是否存在认证用户。
     */
    private void ensureUser(UserPrincipal user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
