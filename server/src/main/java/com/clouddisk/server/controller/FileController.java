package com.clouddisk.server.controller;

import com.clouddisk.server.dto.ApiResponse;
import com.clouddisk.server.dto.DirectoryRequest;
import com.clouddisk.server.dto.FileMetadataDto;
import com.clouddisk.server.exception.BusinessException;
import com.clouddisk.server.exception.ErrorCode;
import com.clouddisk.server.security.UserPrincipal;
import com.clouddisk.server.service.FileService;
import com.clouddisk.server.service.FileSyncService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;
    private final FileSyncService fileSyncService;

    public FileController(FileService fileService, FileSyncService fileSyncService) {
        this.fileService = fileService;
        this.fileSyncService = fileSyncService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FileMetadataDto>>> listFiles(@AuthenticationPrincipal UserPrincipal user) {
        ensureUser(user);
        List<FileMetadataDto> files = fileService.listFiles(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(files));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<FileMetadataDto>> upload(@AuthenticationPrincipal UserPrincipal user,
                                                               @RequestParam("file") MultipartFile file,
                                                               @RequestParam(value = "path", required = false) String path) {
        ensureUser(user);
        FileMetadataDto metadata = fileService.upload(file, path, user.getUserId());
        fileSyncService.notifyChange(user.getUserId(), Map.of("type", "upload", "fileId", metadata.getFileId()));
        return ResponseEntity.ok(ApiResponse.success("上传成功", ErrorCode.SUCCESS.name(), metadata));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal UserPrincipal user,
                                             @PathVariable String fileId) {
        ensureUser(user);
        return fileService.download(fileId, user.getUserId());
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal UserPrincipal user,
                                                    @PathVariable String fileId) {
        ensureUser(user);
        fileService.delete(fileId, user.getUserId());
        fileSyncService.notifyChange(user.getUserId(), Map.of("type", "delete", "fileId", fileId));
        return ResponseEntity.ok(ApiResponse.success("删除成功", ErrorCode.SUCCESS.name(), null));
    }

    @PostMapping("/directories")
    public ResponseEntity<ApiResponse<FileMetadataDto>> createDirectory(@AuthenticationPrincipal UserPrincipal user,
                                                                        @Valid @RequestBody DirectoryRequest request) {
        ensureUser(user);
        FileMetadataDto metadata = fileService.createDirectory(request.getPath(), request.getName(), user.getUserId());
        fileSyncService.notifyChange(user.getUserId(), Map.of("type", "mkdir", "path", request.getPath(), "name", request.getName()));
        return ResponseEntity.ok(ApiResponse.success("目录创建成功", ErrorCode.SUCCESS.name(), metadata));
    }

    @GetMapping("/sync")
    public SseEmitter sync(@AuthenticationPrincipal UserPrincipal user) {
        ensureUser(user);
        return fileSyncService.register(user.getUserId());
    }

    private void ensureUser(UserPrincipal user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
