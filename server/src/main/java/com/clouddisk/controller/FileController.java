package com.clouddisk.controller;

import com.clouddisk.dto.ApiResponse;
import com.clouddisk.dto.DirectoryRequest;
import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.security.UserPrincipal;
import com.clouddisk.service.FileService;
import com.clouddisk.service.FileSyncService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 文件操作接口，包括上传、下载、删除及目录管理。
 */
@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;
    private final FileSyncService fileSyncService;

    public FileController(FileService fileService, FileSyncService fileSyncService) {
        this.fileService = fileService;
        this.fileSyncService = fileSyncService;
    }

    /**
     * 获取当前用户的文件列表。
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FileMetadataDto>>> listFiles(@AuthenticationPrincipal UserPrincipal user) {
        ensureUser(user);
        List<FileMetadataDto> files = fileService.listFiles(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(files));
    }

    /**
     * 上传文件，支持指定目录路径。
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<FileMetadataDto>> upload(@AuthenticationPrincipal UserPrincipal user,
                                                               @RequestParam("file") MultipartFile file,
                                                               @RequestParam(value = "path", required = false) String path) {
        ensureUser(user);
        FileMetadataDto metadata = fileService.upload(file, path, user.getUserId());
        fileSyncService.notifyChange(user.getUserId(), Map.of("type", "upload", "fileId", metadata.getFileId()));
        return ResponseEntity.ok(ApiResponse.success("上传成功", ErrorCode.SUCCESS.name(), metadata));
    }

    /**
     * 下载指定文件。
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal UserPrincipal user,
                                             @PathVariable String fileId) {
        ensureUser(user);
        return fileService.download(fileId, user.getUserId());
    }

    /**
     * 删除指定文件。
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal UserPrincipal user,
                                                    @PathVariable String fileId) {
        ensureUser(user);
        fileService.delete(fileId, user.getUserId());
        fileSyncService.notifyChange(user.getUserId(), Map.of("type", "delete", "fileId", fileId));
        return ResponseEntity.ok(ApiResponse.success("删除成功", ErrorCode.SUCCESS.name(), null));
    }

    /**
     * 创建目录。
     */
    @PostMapping("/directories")
    public ResponseEntity<ApiResponse<FileMetadataDto>> createDirectory(@AuthenticationPrincipal UserPrincipal user,
                                                                        @Valid @RequestBody DirectoryRequest request) {
        ensureUser(user);
        FileMetadataDto metadata = fileService.createDirectory(request.getPath(), request.getName(), user.getUserId());
        fileSyncService.notifyChange(user.getUserId(), Map.of("type", "mkdir", "path", request.getPath(), "name", request.getName()));
        return ResponseEntity.ok(ApiResponse.success("目录创建成功", ErrorCode.SUCCESS.name(), metadata));
    }

    /**
     * 注册 SSE 连接，用于监听文件变更。
     */
    @GetMapping("/sync")
    public SseEmitter sync(@AuthenticationPrincipal UserPrincipal user) {
        ensureUser(user);
        return fileSyncService.register(user.getUserId());
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
