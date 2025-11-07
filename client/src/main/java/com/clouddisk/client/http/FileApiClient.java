package com.clouddisk.client.http;

import com.clouddisk.client.model.FileMetadata;
import com.clouddisk.client.model.FileUploadRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import java.nio.file.Path;
import java.util.List;

public class FileApiClient {
    private final String baseUrl;
    private final CloseableHttpClient httpClient;

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
    public void uploadFile(FileUploadRequest request) {
        // TODO: 实现处理multipart上传逻辑
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