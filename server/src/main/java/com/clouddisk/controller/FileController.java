package com.clouddisk.controller;

import com.clouddisk.dto.ApiResponse;
import com.clouddisk.dto.FileResponse;
import com.clouddisk.dto.FileUploadResponse;
import com.clouddisk.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import java.io.BufferedInputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    public ApiResponse<List<FileResponse>> getUserFiles(@AuthenticationPrincipal String userIdPrincipal) {
        try {
            // 从UserDetails中获取用户ID
            UUID userId = UUID.fromString(userIdPrincipal);
            List<FileResponse> files = fileService.getUserFiles(userId);
            return ApiResponse.success("列表成功", files);
        } catch (Exception e) {
            log.error("获取文件列表失败", e);
            return ApiResponse.error("获取文件列表失败");
        }
    }
    
    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> uploadFile(
            @AuthenticationPrincipal String userIdPrincipal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filePath", required = false, defaultValue = "/") String filePath,
            @RequestParam(value = "contentHash", required = false) String contentHash) {
        
        try {
            // 验证文件
            if (file.isEmpty()) {
                return ApiResponse.error("文件不能为空");
            }
            
            if (file.getSize() > 100 * 1024 * 1024) { // 100MB限制
                return ApiResponse.error("文件大小不能超过100MB");
            }
            
            // 从principal中获取用户ID
            UUID userId = UUID.fromString(userIdPrincipal);

            // 上传文件，传递可选的内容哈希
            FileUploadResponse response = fileService.uploadFile(userId, file, filePath, contentHash);
            return ApiResponse.success("上传成功", response);
            
        } catch (RuntimeException e) {
            log.error("文件上传失败", e);
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return ApiResponse.error("文件上传失败");
        }
    }
    
    /**
     * 下载文件
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @AuthenticationPrincipal String userIdPrincipal,
            @PathVariable UUID fileId) {
        
        try {
            // 从principal中获取用户ID
            UUID userId = UUID.fromString(userIdPrincipal);
            
            // 获取文件信息
            FileResponse fileInfo = fileService.getFileInfo(userId, fileId);
            
            // 以流式方式打开文件内容
            java.io.InputStream stream = new BufferedInputStream(
                    fileService.openFileStream(userId, fileId),
                    8192
            );
            InputStreamResource resource = new InputStreamResource(stream);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + fileInfo.getName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(fileInfo.getFileSize())
                    .body(resource);
                    
        } catch (RuntimeException e) {
            log.error("文件下载失败", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("文件下载失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> deleteFile(
            @AuthenticationPrincipal String userIdPrincipal,
            @PathVariable UUID fileId) {
        
        try {
            // 从principal中获取用户ID
            UUID userId = UUID.fromString(userIdPrincipal);
            
            fileService.deleteFile(userId, fileId);
            return ApiResponse.success("删除成功", null);
            
        } catch (RuntimeException e) {
            log.error("文件删除失败", e);
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("文件删除失败", e);
            return ApiResponse.error("文件删除失败");
        }
    }
    
    /**
     * 获取文件信息
     */
    @GetMapping("/{fileId}")
    public ApiResponse<FileResponse> getFileInfo(
            @AuthenticationPrincipal String userIdPrincipal,
            @PathVariable UUID fileId) {
        
        try {
            // 从principal中获取用户ID
            UUID userId = UUID.fromString(userIdPrincipal);
            
            FileResponse fileInfo = fileService.getFileInfo(userId, fileId);
            return ApiResponse.success("获取成功", fileInfo);
            
        } catch (RuntimeException e) {
            log.error("获取文件信息失败", e);
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取文件信息失败", e);
            return ApiResponse.error("获取文件信息失败");
        }
    }
}