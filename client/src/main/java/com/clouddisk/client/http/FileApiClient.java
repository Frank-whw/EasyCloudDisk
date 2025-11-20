package com.clouddisk.client.http;

import com.clouddisk.client.model.ApiResponse;
import com.clouddisk.client.model.FileMetadata;
import com.clouddisk.client.model.FileResponse;
import com.clouddisk.client.model.FileUploadRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import com.clouddisk.client.util.RetryTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文件API客户端
 * 用于与服务器进行文件相关的操作，如上传、下载、删除和列出文件
 */
@Slf4j
public class FileApiClient {
    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    private String authToken;

    public FileApiClient(String baseUrl) {
        this(baseUrl, HttpClients.createDefault());
    }

    public FileApiClient(String baseUrl, CloseableHttpClient httpClient) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
    }
    
    /**
     * 设置认证令牌
     * @param token 认证令牌
     */
    public void setAuthToken(String token) {
        this.authToken = token;
    }
    
    /**
     * 获取文件列表
     * @return 文件响应列表
     */
    public List<FileResponse> listFiles() {
        return RetryTemplate.executeWithRetry(() -> {
            try {
                HttpGet httpGet = new HttpGet(baseUrl + "/files");
                
                // 设置认证头
                if (authToken != null && !authToken.isEmpty()) {
                    httpGet.setHeader("Authorization", "Bearer " + authToken);
                }
                
                // 执行请求
                return httpClient.execute(httpGet, response -> {
                    int statusCode = response.getCode();
                    if (statusCode >= 200 && statusCode < 300) {
                        // 解析响应
                        ObjectMapper mapper = new ObjectMapper();
                        // 配置ObjectMapper以支持Java 8时间类型
                        mapper.findAndRegisterModules();
                        
                        ApiResponse<List<FileResponse>> apiResponse = mapper.readValue(
                            response.getEntity().getContent(),
                            new TypeReference<ApiResponse<List<FileResponse>>>() {}
                        );
                        
                        if (apiResponse.isSuccess()) {
                            List<FileResponse> files = apiResponse.getData();
                            log.debug("获取文件列表成功,共 {} 个文件", files != null ? files.size() : 0);
                            return files != null ? files : new ArrayList<>();
                        } else {
                            log.error("获取文件列表失败: {}", apiResponse.getMessage());
                            throw new RuntimeException("获取文件列表失败: " + apiResponse.getMessage());
                        }
                    } else {
                        log.error("获取文件列表失败，状态码: {}", statusCode);
                        throw new RuntimeException("获取文件列表失败，状态码: " + statusCode);
                    }
                });
            } catch (Exception e) {
                log.error("获取文件列表过程中发生错误", e);
                throw new RuntimeException("获取文件列表失败: " + e.getMessage(), e);
            }
        }, 3); // 重试3次
    }
    
    /**
     * 上传文件
     * @param request 文件上传请求
     */
    public boolean uploadFile(FileUploadRequest request) {
        return RetryTemplate.executeWithRetry(() -> {
            try {
                // 创建上传请求
                HttpPost httpPost = new HttpPost(baseUrl + "/files/upload");
                
                // 设置认证头
                if (authToken != null && !authToken.isEmpty()) {
                    httpPost.setHeader("Authorization", "Bearer " + authToken);
                }
                
                // 构建multipart/form-data
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                String filename = request.getLocalPath() != null ? request.getLocalPath().getFileName().toString() : "upload.bin";
                if (request.getCompressedPayload() != null) {
                    builder.addBinaryBody("file", request.getCompressedPayload(), ContentType.APPLICATION_OCTET_STREAM, filename);
                }
                if (request.getFilePath() != null) {
                    // 服务器使用 "path" 参数接收目录信息，为兼容旧版本同时发送 filePath
                    builder.addTextBody("path", request.getFilePath());
                    builder.addTextBody("filePath", request.getFilePath());
                }
                if (request.getContentHash() != null) {
                    builder.addTextBody("contentHash", request.getContentHash());
                }
                httpPost.setEntity(builder.build());
                
                // 执行请求
                return httpClient.execute(httpPost, response -> {
                    int statusCode = response.getCode();
                    if (statusCode >= 200 && statusCode < 300) {
                        log.info("文件上传成功: {}", request.getFilePath());
                        return true;
                    } else {
                        log.error("文件上传失败，状态码: {}", statusCode);
                        throw new RuntimeException("文件上传失败，状态码: " + statusCode);
                    }
                });
            } catch (Exception e) {
                log.error("文件上传过程中发生错误: {}", request.getFilePath(), e);
                throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
            }
        }, 3); // 重试3次
    }
    
    /**
     * 下载文件
     * @param fileId 文件ID
     * @param target 目标路径
     */
    public boolean downloadFile(String fileId, Path target) {
        return RetryTemplate.executeWithRetry(() -> {
            try {
                HttpGet httpGet = new HttpGet(baseUrl + "/files/" + fileId + "/download");
                
                // 设置认证头
                if (authToken != null && !authToken.isEmpty()) {
                    httpGet.setHeader("Authorization", "Bearer " + authToken);
                }
                
                // 执行请求
                return httpClient.execute(httpGet, response -> {
                    int statusCode = response.getCode();
                    if (statusCode >= 200 && statusCode < 300) {
                        // 保存文件到目标路径（流式拷贝，避免一次性加载到内存）
                        try (java.io.InputStream in = response.getEntity().getContent();
                             java.io.OutputStream out = java.nio.file.Files.newOutputStream(target);
                             java.io.BufferedInputStream bin = new java.io.BufferedInputStream(in);
                             java.io.BufferedOutputStream bout = new java.io.BufferedOutputStream(out)) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = bin.read(buffer)) != -1) {
                                bout.write(buffer, 0, len);
                            }
                            bout.flush();
                        }
                        log.info("文件下载成功: {} -> {}", fileId, target);
                        return true;
                    } else {
                        log.error("文件下载失败，状态码: {}", statusCode);
                        throw new RuntimeException("文件下载失败，状态码: " + statusCode);
                    }
                });
            } catch (Exception e) {
                log.error("文件下载过程中发生错误: {}", fileId, e);
                throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
            }
        }, 3); // 重试3次
    }
    
    /**
     * 检查文件是否已存在（用于去重验证）
     * @param contentHash 文件内容哈希
     * @return true表示文件不存在，需要上传；false表示文件已存在，跳过上传
     */
    public boolean checkFileExists(String contentHash) {
        return RetryTemplate.executeWithRetry(() -> {
            try {
                HttpGet httpGet = new HttpGet(baseUrl + "/files/check?contentHash=" + contentHash);
                
                // 设置认证头
                if (authToken != null && !authToken.isEmpty()) {
                    httpGet.setHeader("Authorization", "Bearer " + authToken);
                }
                
                // 执行请求
                return httpClient.execute(httpGet, response -> {
                    int statusCode = response.getCode();
                    if (statusCode == 200) {
                        // 文件已存在，不需要上传
                        log.info("文件已存在，跳过上传 (哈希: {})", contentHash);
                        return false;
                    } else if (statusCode == 404) {
                        // 文件不存在，需要上传
                        log.debug("文件不存在，需要上传 (哈希: {})", contentHash);
                        return true;
                    } else {
                        log.error("检查文件存在性失败，状态码: {}", statusCode);
                        throw new RuntimeException("检查文件存在性失败，状态码: " + statusCode);
                    }
                });
            } catch (Exception e) {
                log.error("检查文件存在性时发生错误 (哈希: {})", contentHash, e);
                throw new RuntimeException("检查文件存在性失败: " + e.getMessage(), e);
            }
        }, 3); // 重试3次
    }
    
    /**
     * 通知服务端上传完成（用于S3直接上传后的通知）
     * @param contentHash 文件内容哈希
     * @param filePath 文件路径
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @return 通知是否成功
     */
    public boolean notifyUploadComplete(String contentHash, String filePath, String fileName, long fileSize) {
        return RetryTemplate.executeWithRetry(() -> {
            try {
                HttpPost httpPost = new HttpPost(baseUrl + "/files/notify-upload");
                
                // 设置认证头
                if (authToken != null && !authToken.isEmpty()) {
                    httpPost.setHeader("Authorization", "Bearer " + authToken);
                }
                
                // 构建请求体
                ObjectMapper mapper = new ObjectMapper();
                mapper.findAndRegisterModules();
                String jsonBody = mapper.writeValueAsString(Map.of(
                        "contentHash", contentHash,
                        "filePath", filePath,
                        "fileName", fileName,
                        "fileSize", fileSize
                ));
                httpPost.setEntity(new ByteArrayEntity(jsonBody.getBytes(StandardCharsets.UTF_8), ContentType.APPLICATION_JSON));
                
                // 执行请求
                return httpClient.execute(httpPost, response -> {
                    int statusCode = response.getCode();
                    if (statusCode >= 200 && statusCode < 300) {
                        log.info("通知服务端上传完成成功 (哈希: {})", contentHash);
                        return true;
                    } else {
                        log.error("通知服务端上传完成失败，状态码: {}", statusCode);
                        throw new RuntimeException("通知服务端上传完成失败，状态码: " + statusCode);
                    }
                });
            } catch (Exception e) {
                log.error("通知服务端上传完成时发生错误 (哈希: {})", contentHash, e);
                throw new RuntimeException("通知服务端上传完成失败: " + e.getMessage(), e);
            }
        }, 3); // 重试3次
    }
    
    /**
     * 删除文件
     * @param fileId 文件ID
     */
    public boolean deleteFile(String fileId) {
        return RetryTemplate.executeWithRetry(() -> {
            try {
                HttpDelete httpDelete = new HttpDelete(baseUrl + "/files/" + fileId);
                
                // 设置认证头
                if (authToken != null && !authToken.isEmpty()) {
                    httpDelete.setHeader("Authorization", "Bearer " + authToken);
                }
                
                // 执行请求
                return httpClient.execute(httpDelete, response -> {
                    int statusCode = response.getCode();
                    if (statusCode >= 200 && statusCode < 300) {
                        log.info("文件删除成功: {}", fileId);
                        return true;
                    } else {
                        log.error("文件删除失败，状态码: {}", statusCode);
                        throw new RuntimeException("文件删除失败，状态码: " + statusCode);
                    }
                });
            } catch (Exception e) {
                log.error("文件删除过程中发生错误: {}", fileId, e);
                throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
            }
        }, 3); // 重试3次
    }
}