package com.clouddisk.controller;

import com.clouddisk.dto.ApiResponse;
import com.clouddisk.dto.ConflictResolutionRequest;
import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.dto.FileVersionDto;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.security.UserPrincipal;
import com.clouddisk.service.ConflictResolutionService;
import com.clouddisk.service.FileVersionService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件版本管理和冲突解决接口
 */
@RestController
@RequestMapping("/files")
public class FileVersionController {
    
    private final FileVersionService fileVersionService;
    private final ConflictResolutionService conflictResolutionService;
    
    public FileVersionController(FileVersionService fileVersionService,
                               ConflictResolutionService conflictResolutionService) {
        this.fileVersionService = fileVersionService;
        this.conflictResolutionService = conflictResolutionService;
    }
    
    /**
     * 获取文件版本历史
     */
    @GetMapping("/{fileId}/versions")
    public ResponseEntity<ApiResponse<List<FileVersionDto>>> getVersionHistory(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String fileId) {
        ensureUser(user);
        List<FileVersionDto> versions = fileVersionService.getVersionHistory(fileId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(versions));
    }
    
    /**
     * 下载指定版本的文件
     */
    @GetMapping("/{fileId}/versions/{versionNumber}/download")
    public ResponseEntity<Resource> downloadVersion(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String fileId,
            @PathVariable Integer versionNumber) {
        ensureUser(user);
        return fileVersionService.downloadVersion(fileId, versionNumber, user.getUserId());
    }
    
    /**
     * 恢复到指定版本
     */
    @PostMapping("/{fileId}/restore/{versionNumber}")
    public ResponseEntity<ApiResponse<FileMetadataDto>> restoreVersion(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String fileId,
            @PathVariable Integer versionNumber) {
        ensureUser(user);
        FileMetadataDto result = fileVersionService.restoreVersion(fileId, versionNumber, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success("版本恢复成功", ErrorCode.SUCCESS.name(), result));
    }
    
    /**
     * 检查文件冲突
     */
    @PostMapping("/{fileId}/check-conflict")
    public ResponseEntity<ApiResponse<Void>> checkConflict(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String fileId,
            @RequestParam(required = false) Long expectedVersion) {
        ensureUser(user);
        conflictResolutionService.checkForConflicts(fileId, expectedVersion, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success("无冲突", ErrorCode.SUCCESS.name(), null));
    }
    
    /**
     * 解决文件冲突
     */
    @PostMapping("/{fileId}/resolve-conflict")
    public ResponseEntity<ApiResponse<FileMetadataDto>> resolveConflict(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String fileId,
            @RequestParam("file") MultipartFile file,
            @Valid @RequestBody ConflictResolutionRequest request) {
        ensureUser(user);
        FileMetadataDto result = conflictResolutionService.resolveConflict(fileId, user.getUserId(), file, request);
        return ResponseEntity.ok(ApiResponse.success("冲突解决成功", ErrorCode.SUCCESS.name(), result));
    }
    
    /**
     * 校验当前请求是否存在认证用户
     */
    private void ensureUser(UserPrincipal user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
