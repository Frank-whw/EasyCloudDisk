package com.clouddisk.controller;

import com.clouddisk.dto.ApiResponse;
import com.clouddisk.dto.CreateShareRequest;
import com.clouddisk.dto.FileShareDto;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.security.UserPrincipal;
import com.clouddisk.service.FileShareService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文件共享控制器
 */
@RestController
@RequestMapping
public class FileShareController {

    private final FileShareService fileShareService;

    public FileShareController(FileShareService fileShareService) {
        this.fileShareService = fileShareService;
    }

    /**
     * 创建文件共享
     */
    @PostMapping("/files/{fileId}/share")
    public ResponseEntity<ApiResponse<FileShareDto>> createShare(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String fileId,
            @Valid @RequestBody CreateShareRequest request) {
        ensureUser(user);
        request.setFileId(fileId); // 确保fileId一致
        FileShareDto share = fileShareService.createShare(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("共享创建成功", ErrorCode.SUCCESS.name(), share));
    }

    /**
     * 获取用户的所有共享
     */
    @GetMapping("/shares")
    public ResponseEntity<ApiResponse<List<FileShareDto>>> listUserShares(
            @AuthenticationPrincipal UserPrincipal user) {
        ensureUser(user);
        List<FileShareDto> shares = fileShareService.listUserShares(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(shares));
    }

    /**
     * 获取文件的共享信息
     */
    @GetMapping("/files/{fileId}/share")
    public ResponseEntity<ApiResponse<FileShareDto>> getFileShare(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String fileId) {
        ensureUser(user);
        Optional<FileShareDto> share = fileShareService.getFileShare(fileId, user.getUserId());
        if (share.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(share.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.success("文件未共享", ErrorCode.SUCCESS.name(), null));
        }
    }

    /**
     * 取消文件共享
     */
    @DeleteMapping("/shares/{shareId}")
    public ResponseEntity<ApiResponse<Void>> cancelShare(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String shareId) {
        ensureUser(user);
        fileShareService.cancelShare(shareId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success("共享已取消", ErrorCode.SUCCESS.name(), null));
    }

    /**
     * 公开访问：获取共享信息（无需认证）
     */
    @GetMapping("/shares/{shareId}/info")
    public ResponseEntity<ApiResponse<FileShareDto>> getShareInfo(@PathVariable String shareId) {
        FileShareDto share = fileShareService.getShareInfo(shareId);
        return ResponseEntity.ok(ApiResponse.success(share));
    }

    /**
     * 公开访问：验证共享密码（无需认证）
     */
    @PostMapping("/shares/{shareId}/validate")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> validateSharePassword(
            @PathVariable String shareId,
            @RequestBody Map<String, String> request) {
        String password = request.get("password");
        boolean valid = fileShareService.validateSharePassword(shareId, password);
        return ResponseEntity.ok(ApiResponse.success(Map.of("valid", valid)));
    }

    /**
     * 公开访问：下载共享文件（无需认证）
     */
    @GetMapping("/shares/{shareId}/download")
    public ResponseEntity<Resource> downloadSharedFile(
            @PathVariable String shareId,
            @RequestParam(required = false) String password) {
        return fileShareService.downloadSharedFile(shareId, password);
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
