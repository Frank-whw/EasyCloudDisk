package com.clouddisk.controller;

import com.clouddisk.dto.ApiResponse;
import com.clouddisk.dto.FileResponse;
import com.clouddisk.dto.FileUploadResponse;
import com.clouddisk.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
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
    public ApiResponse<List<FileResponse>> getUserFiles(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            // 从UserDetails中获取用户ID
            UUID userId = UUID.fromString(userDetails.getUsername());
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
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filePath", required = false, defaultValue = "/") String filePath) {
        
        try {
            // 验证文件
            if (file.isEmpty()) {
                return ApiResponse.error("文件不能为空");
            }
            
            if (file.getSize() > 100 * 1024 * 1024) { // 100MB限制
                return ApiResponse.error("文件大小不能超过100MB");
            }
            
            // 从UserDetails中获取用户ID
            UUID userId = UUID.fromString(userDetails.getUsername());
            
            FileUploadResponse response = fileService.uploadFile(userId, file, filePath);
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
    public ResponseEntity<ByteArrayResource> downloadFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID fileId) {
        
        try {
            // 从UserDetails中获取用户ID
            UUID userId = UUID.fromString(userDetails.getUsername());
            
            // 获取文件信息
            FileResponse fileInfo = fileService.getFileInfo(userId, fileId);
            
            // 下载文件内容
            byte[] fileContent = fileService.downloadFile(userId, fileId);
            
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + fileInfo.getName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(fileContent.length)
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
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID fileId) {
        
        try {
            // 从UserDetails中获取用户ID
            UUID userId = UUID.fromString(userDetails.getUsername());
            
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
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID fileId) {
        
        try {
            // 从UserDetails中获取用户ID
            UUID userId = UUID.fromString(userDetails.getUsername());
            
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