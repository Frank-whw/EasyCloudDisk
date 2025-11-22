package com.clouddisk.controller;

import com.clouddisk.dto.*;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.security.UserPrincipal;
import com.clouddisk.service.AdvancedUploadService;
import com.clouddisk.service.DiffSyncService;
import com.clouddisk.service.EncryptionService;
import com.clouddisk.service.FileService;
import com.clouddisk.service.FileSyncService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件操作接口，包括上传、下载、删除及目录管理。
 */
@Slf4j
@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;
    private final FileSyncService fileSyncService;
    private final AdvancedUploadService advancedUploadService;
    private final DiffSyncService diffSyncService;
    private final EncryptionService encryptionService;

    public FileController(FileService fileService, FileSyncService fileSyncService, 
                         AdvancedUploadService advancedUploadService, DiffSyncService diffSyncService,
                         EncryptionService encryptionService) {
        this.fileService = fileService;
        this.fileSyncService = fileSyncService;
        this.advancedUploadService = advancedUploadService;
        this.diffSyncService = diffSyncService;
        this.encryptionService = encryptionService;
    }

    /**
     * 获取当前用户的文件列表。
     * @param path 可选参数，指定要查询的目录路径，默认为根目录"/"
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FileMetadataDto>>> listFiles(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(value = "path", required = false) String path) {
        ensureUser(user);
        List<FileMetadataDto> files = fileService.listFiles(user.getUserId(), path);
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
     * 检查文件是否可以秒传
     */
    @PostMapping("/quick-check")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> quickCheck(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody Map<String, String> request) {
        ensureUser(user);
        String hash = request.get("hash");
        boolean canQuickUpload = advancedUploadService.checkQuickUpload(hash, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("canQuickUpload", canQuickUpload)));
    }

    /**
     * 客户端校验某内容哈希是否存在，以便跳过上传
     * 约定：存在返回 200，不存在返回 404
     */
    @GetMapping("/check")
    public ResponseEntity<Void> checkFileExists(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam("contentHash") String contentHash) {
        try {
            // 验证用户
            ensureUser(user);
            log.debug("检查文件存在性: userId={}, contentHash={}", user.getUserId(), contentHash);
            
            // 验证哈希格式
            if (contentHash == null || contentHash.trim().isEmpty()) {
                log.warn("哈希值为空");
                return ResponseEntity.badRequest().build();
            }
            
            if (!contentHash.matches("[0-9a-fA-F]{64}")) {
                log.warn("哈希格式无效: {}", contentHash);
                return ResponseEntity.badRequest().build();
            }
            
            // 空文件哈希特殊处理
            if ("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".equals(contentHash)) {
                log.debug("空文件哈希，返回不存在");
                return ResponseEntity.status(404).build();
            }
            
            // 检查文件是否存在
            boolean exists = advancedUploadService.checkQuickUpload(contentHash, user.getUserId());
            if (exists) {
                log.debug("文件已存在: contentHash={}", contentHash);
                return ResponseEntity.ok().build();
            }
            
            log.debug("文件不存在: contentHash={}", contentHash);
            return ResponseEntity.status(404).build();
            
        } catch (Exception ex) {
            log.error("检查文件存在性时发生异常: contentHash={}, userId={}", 
                contentHash, user != null ? user.getUserId() : "null", ex);
            // 出错时返回404，让客户端继续上传
            return ResponseEntity.status(404).build();
        }
    }

    /**
     * 执行秒传
     */
    @PostMapping("/quick-upload")
    public ResponseEntity<ApiResponse<FileMetadataDto>> quickUpload(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody QuickUploadRequest request) {
        ensureUser(user);
        FileMetadataDto metadata = advancedUploadService.quickUpload(
                request.getHash(),
                request.getFileName(),
                request.getPath(),
                user.getUserId()
        );
        fileSyncService.notifyChange(user.getUserId(), Map.of("type", "quick-upload", "fileId", metadata.getFileId()));
        return ResponseEntity.ok(ApiResponse.success("秒传成功", ErrorCode.SUCCESS.name(), metadata));
    }

    /**
     * 客户端通知上传完成，兼容客户端 FileApiClient.notifyUploadComplete
     * 用于OSS直接上传后，在数据库中创建文件记录
     */
    @PostMapping("/notify-upload")
    public ResponseEntity<ApiResponse<FileMetadataDto>> notifyUploadComplete(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody Map<String, Object> request) {
        ensureUser(user);
        String contentHash = (String) request.get("contentHash");
        String filePath = (String) request.get("filePath");
        String fileName = (String) request.get("fileName");
        Long fileSize = request.get("fileSize") != null ? 
                        Long.valueOf(request.get("fileSize").toString()) : 0L;
        
        // 调用服务创建文件记录
        FileMetadataDto metadata = advancedUploadService.createFileAfterOssUpload(
                contentHash, filePath, fileName, fileSize, user.getUserId());
        
        // 通知文件同步服务
        fileSyncService.notifyChange(user.getUserId(), Map.of(
                "type", "upload", 
                "fileId", metadata.getFileId()));
        
        return ResponseEntity.ok(ApiResponse.success("文件上传完成", ErrorCode.SUCCESS.name(), metadata));
    }

    /**
     * 初始化断点续传会话
     */
    @PostMapping("/resumable/init")
    public ResponseEntity<ApiResponse<UploadSessionDto>> initResumableUpload(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody Map<String, Object> request) {
        ensureUser(user);
        String fileName = (String) request.get("fileName");
        String path = (String) request.get("path");
        Long fileSize = Long.valueOf(request.get("fileSize").toString());
        
        UploadSessionDto session = advancedUploadService.initResumableUpload(fileName, path, fileSize, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success("会话创建成功", ErrorCode.SUCCESS.name(), session));
    }

    /**
     * 上传单个分块
     */
    @PostMapping("/resumable/{sessionId}/chunk/{chunkIndex}")
    public ResponseEntity<ApiResponse<UploadSessionDto>> uploadChunk(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String sessionId,
            @PathVariable Integer chunkIndex,
            @RequestParam("chunk") MultipartFile chunk) {
        ensureUser(user);
        UploadSessionDto session = advancedUploadService.uploadChunk(sessionId, chunkIndex, chunk, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success("分块上传成功", ErrorCode.SUCCESS.name(), session));
    }

    /**
     * 完成断点续传
     */
    @PostMapping("/resumable/{sessionId}/complete")
    public ResponseEntity<ApiResponse<FileMetadataDto>> completeResumableUpload(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String sessionId) {
        ensureUser(user);
        FileMetadataDto metadata = advancedUploadService.completeResumableUpload(sessionId, user.getUserId());
        fileSyncService.notifyChange(user.getUserId(), Map.of("type", "upload", "fileId", metadata.getFileId()));
        return ResponseEntity.ok(ApiResponse.success("文件上传完成", ErrorCode.SUCCESS.name(), metadata));
    }

    /**
     * 获取用户的所有上传会话
     */
    @GetMapping("/resumable/sessions")
    public ResponseEntity<ApiResponse<List<UploadSessionDto>>> listSessions(
            @AuthenticationPrincipal UserPrincipal user) {
        ensureUser(user);
        List<UploadSessionDto> sessions = advancedUploadService.listSessions(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    /**
     * 获取文件的块签名（用于差分同步）
     */
    @GetMapping("/{fileId}/signatures")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFileSignatures(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String fileId) {
        ensureUser(user);
        List<Map<String, Object>> signatures = diffSyncService.getFileSignatures(fileId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(signatures));
    }

    /**
     * 应用差分更新
     */
    @PostMapping("/{fileId}/delta")
    public ResponseEntity<ApiResponse<FileMetadataDto>> applyDelta(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String fileId,
            @RequestBody Map<String, Object> request) {
        ensureUser(user);
        // TODO: 解析deltaChunks（需要处理二进制数据传输）
        Map<Integer, byte[]> deltaChunks = new HashMap<>();
        
        FileMetadataDto metadata = diffSyncService.applyDelta(fileId, user.getUserId(), deltaChunks);
        fileSyncService.notifyChange(user.getUserId(), Map.of("type", "delta-update", "fileId", fileId));
        return ResponseEntity.ok(ApiResponse.success("差分更新成功", ErrorCode.SUCCESS.name(), metadata));
    }

    /**
     * 上传加密文件（客户端已加密）
     */
    @PostMapping("/upload-encrypted")
    public ResponseEntity<ApiResponse<FileMetadataDto>> uploadEncrypted(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam("file") MultipartFile file,
            @RequestParam("metadata") String metadataJson) {
        ensureUser(user);
        
        // 解析加密元数据
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            EncryptedUploadRequest request = mapper.readValue(metadataJson, EncryptedUploadRequest.class);
            
            FileMetadataDto metadata = encryptionService.uploadEncryptedFile(file, request, user.getUserId());
            fileSyncService.notifyChange(user.getUserId(), Map.of("type", "encrypted-upload", "fileId", metadata.getFileId()));
            return ResponseEntity.ok(ApiResponse.success("加密文件上传成功", ErrorCode.SUCCESS.name(), metadata));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "解析加密元数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件的加密元数据
     */
    @GetMapping("/{fileId}/encryption")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEncryptionMetadata(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String fileId) {
        ensureUser(user);
        Map<String, Object> metadata = encryptionService.getEncryptionMetadata(fileId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(metadata));
    }

    /**
     * 检查收敛加密文件是否可以秒传
     */
    @PostMapping("/convergent-check")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkConvergentQuickUpload(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody Map<String, String> request) {
        ensureUser(user);
        String originalHash = request.get("originalHash");
        boolean canQuickUpload = encryptionService.checkConvergentQuickUpload(originalHash, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("canQuickUpload", canQuickUpload)));
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
