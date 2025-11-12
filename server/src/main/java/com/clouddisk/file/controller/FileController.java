package com.clouddisk.file.controller;

import com.clouddisk.common.dto.ApiResponse;
import com.clouddisk.common.dto.FileResponse;
import com.clouddisk.common.dto.FileUploadResponse;
import com.clouddisk.common.exception.BusinessException;
import com.clouddisk.file.dto.NotifyUploadRequest;
import com.clouddisk.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import java.io.BufferedInputStream;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {
    
    private final FileService fileService;
    
    /**
     * 获取用户文件列表
     */
    @GetMapping
    public ApiResponse<List<FileResponse>> getUserFiles(@AuthenticationPrincipal Object principal) {
        UUID userId = resolveUserId(principal);
        List<FileResponse> files = fileService.getUserFiles(userId);
        return ApiResponse.success("列表成功", files);
    }
    
    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> uploadFile(
            @AuthenticationPrincipal Object principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filePath", required = false, defaultValue = "/") String filePath,
            @RequestParam(value = "contentHash", required = false) String contentHash) {
        // 验证文件
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空", 400);
        }

        if (file.getSize() > 100 * 1024 * 1024) { // 100MB限制
            throw new BusinessException("文件大小不能超过100MB", 413);
        }

        UUID userId = resolveUserId(principal);

        // 上传文件，传递可选的内容哈希
        FileUploadResponse response = fileService.uploadFile(userId, file, filePath, contentHash);
        return ApiResponse.success("上传成功", response);
    }
    
    /**
     * 下载文件
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @AuthenticationPrincipal Object principal,
            @PathVariable("fileId") UUID fileId) {
        UUID userId = resolveUserId(principal);

        // 获取文件信息
        FileResponse fileInfo = fileService.getFileInfo(userId, fileId);

        // 以流式方式打开文件内容
        java.io.InputStream stream = new BufferedInputStream(
                fileService.openFileStream(userId, fileId),
                8192
        );
        InputStreamResource resource = new InputStreamResource(stream);

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(fileInfo.getName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .headers(headers -> headers.setContentDisposition(contentDisposition))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(fileInfo.getFileSize())
                .body(resource);
    }
    
    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> deleteFile(
            @AuthenticationPrincipal Object principal,
            @PathVariable("fileId") UUID fileId) {
        UUID userId = resolveUserId(principal);

        fileService.deleteFile(userId, fileId);
        return ApiResponse.success("删除成功", null);
    }
    
    /**
     * 检查文件是否已存在（用于去重验证）
     * 返回200表示文件已存在，404表示文件不存在
     */
    @GetMapping("/check")
    public ResponseEntity<ApiResponse<Void>> checkFileExists(
            @AuthenticationPrincipal Object principal,
            @RequestParam("contentHash") String contentHash) {
        UUID userId = resolveUserId(principal);

        boolean exists = fileService.checkFileExists(userId, contentHash);
        if (exists) {
            return ResponseEntity.status(200).body(ApiResponse.success("文件已存在", null));
        } else {
            return ResponseEntity.status(404).body(ApiResponse.error("文件不存在", 404));
        }
    }
    
    /**
     * 通知服务端上传完成（用于S3直接上传后的通知）
     */
    @PostMapping("/notify-upload")
    public ApiResponse<FileUploadResponse> notifyUploadComplete(
            @AuthenticationPrincipal Object principal,
            @RequestBody NotifyUploadRequest request) {
        UUID userId = resolveUserId(principal);

        FileUploadResponse response = fileService.notifyUploadComplete(
            userId, request.getContentHash(), request.getFilePath(), request.getFileSize());
        return ApiResponse.success("通知成功", response);
    }
    
    /**
     * 获取文件信息
     */
    @GetMapping("/{fileId}")
    public ApiResponse<FileResponse> getFileInfo(
            @AuthenticationPrincipal Object principal,
            @PathVariable("fileId") UUID fileId) {
        UUID userId = resolveUserId(principal);

        FileResponse fileInfo = fileService.getFileInfo(userId, fileId);
        return ApiResponse.success("获取成功", fileInfo);
    }

    private UUID resolveUserId(Object principal) {
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        if (principal instanceof String userId && !userId.isBlank()) {
            return parseUuid(userId);
        }
        if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            if (username != null && !username.isBlank()) {
                return parseUuid(username);
            }
        }
        throw new BusinessException("用户身份信息缺失", 401);
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("用户身份信息格式非法", 401);
        }
    }
}