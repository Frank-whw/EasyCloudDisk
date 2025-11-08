package com.clouddisk.client.http;

import com.clouddisk.client.model.ApiResponse;
import com.clouddisk.client.model.FileMetadata;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
     * @return 文件元数据列表
     */
    public List<FileMetadata> listFiles() {
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
                    ApiResponse<List<FileMetadata>> apiResponse = mapper.readValue(
                        response.getEntity().getContent(),
                        new TypeReference<ApiResponse<List<FileMetadata>>>() {}
                    );
                    
                    if (apiResponse.isSuccess()) {
                        log.info("获取文件列表成功，共 {} 个文件", apiResponse.getData().size());
                        return apiResponse.getData();
                    } else {
                        log.error("获取文件列表失败: {}", apiResponse.getMessage());
                        return new ArrayList<>();
                    }
                } else {
                    log.error("获取文件列表失败，状态码: {}", statusCode);
                    return new ArrayList<>();
                }
            });
        } catch (Exception e) {
            log.error("获取文件列表过程中发生错误", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 上传文件
     * @param request 文件上传请求
     */
    public boolean uploadFile(FileUploadRequest request) {
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
                    return false;
                }
            });
        } catch (Exception e) {
            log.error("文件上传过程中发生错误: {}", request.getFilePath(), e);
            return false;
        }
    }
    
    /**
     * 下载文件
     * @param fileId 文件ID
     * @param target 目标路径
     */
    public boolean downloadFile(String fileId, Path target) {
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
                    return false;
                }
            });
        } catch (Exception e) {
            log.error("文件下载过程中发生错误: {}", fileId, e);
            return false;
        }
    }
    
    /**
     * 删除文件
     * @param fileId 文件ID
     */
    public boolean deleteFile(String fileId) {
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
                    return false;
                }
            });
        } catch (Exception e) {
            log.error("文件删除过程中发生错误: {}", fileId, e);
            return false;
        }
    }
}