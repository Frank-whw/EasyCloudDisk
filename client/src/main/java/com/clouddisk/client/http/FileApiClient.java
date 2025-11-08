package com.clouddisk.client.http;

import com.clouddisk.client.model.FileMetadata;
import com.clouddisk.client.model.FileUploadRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.nio.file.Path;
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

    public FileApiClient(CloseableHttpClient httpClient) {
        this("http://ec2-54-95-61-230.ap-northeast-1.compute.amazonaws.com:8080", httpClient);
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
        // TODO: 实现获取文件列表逻辑
        return null;
    }
    
    /**
     * 上传文件
     * @param request 文件上传请求
     */
    public boolean uploadFile(FileUploadRequest request) {
        try {
            // 创建上传请求
            HttpPost httpPost = new HttpPost(baseUrl + "/files/upload");
            
            // 设置请求头
            httpPost.setHeader("Content-Type", "application/octet-stream");
            if (authToken != null && !authToken.isEmpty()) {
                httpPost.setHeader("Authorization", "Bearer " + authToken);
            }
            
            // 设置文件内容
            if (request.getCompressedPayload() != null) {
                httpPost.setEntity(new ByteArrayEntity(request.getCompressedPayload(), ContentType.DEFAULT_BINARY));
            }
            
            // 添加查询参数
            StringBuilder urlBuilder = new StringBuilder(baseUrl + "/files/upload");
            urlBuilder.append("?filePath=").append(request.getFilePath());
            if (request.getContentHash() != null) {
                urlBuilder.append("&contentHash=").append(request.getContentHash());
            }
            
            httpPost.setUri(java.net.URI.create(urlBuilder.toString()));
            
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
    public void downloadFile(String fileId, Path target) {
        // TODO: 实现二进制下载逻辑
    }
    
    /**
     * 删除文件
     * @param fileId 文件ID
     */
    public void deleteFile(String fileId) {
        // TODO: 实现删除文件逻辑
    }
}