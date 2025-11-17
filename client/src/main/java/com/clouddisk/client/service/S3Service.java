package com.clouddisk.client.service;

import com.clouddisk.client.model.FileUploadRequest;
import com.clouddisk.client.config.ClientProperties;
import com.clouddisk.client.util.RetryTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.core.retry.RetryMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.net.URI;

/**
 * 封装与 AWS S3 交互的所有操作，包括上传、下载、删除和列出对象。
 */
@Slf4j
@Service
public class S3Service {
    private S3Client s3Client;
    private String bucketName;
    private final ClientProperties clientProperties;
    private long partSizeBytes;
    private int maxRetries;
    
    public S3Service(ClientProperties clientProperties) {
        this.clientProperties = clientProperties;
    }
    
    /**
     * 初始化 S3 客户端，在 Spring 构建 Bean 后执行。
     */
    @PostConstruct
    public void initialize() {
        // 初始化 Bucket 名称（配置优先，其次环境变量）
        this.bucketName = clientProperties.getS3Bucket();
        if (this.bucketName == null || this.bucketName.isBlank()) {
            this.bucketName = System.getenv().getOrDefault("AWS_S3_BUCKET", System.getenv("AWS_S3_BUCKET_NAME"));
        }
        if (this.bucketName == null || this.bucketName.isBlank()) {
            log.warn("未配置 S3 bucket 名称。请设置 client.s3Bucket 或 AWS_S3_BUCKET 环境变量。");
        }

        // 分片大小与重试次数
        this.partSizeBytes = Math.max(5L * 1024 * 1024, clientProperties.getS3PartSizeMb() * 1024L * 1024L);
        this.maxRetries = Math.max(1, clientProperties.getS3MaxRetries());

        // 配置 HTTP 客户端：连接池与超时
        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder()
                .maxConnections(clientProperties.getHttpMaxConnections())
                .connectionTimeout(Duration.ofMillis(clientProperties.getHttpConnTimeoutMs()))
                .socketTimeout(Duration.ofMillis(clientProperties.getHttpReadTimeoutMs()));

        // 构建 S3 客户端，支持 endpoint/path-style 与重试
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(clientProperties.getS3Region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(Boolean.TRUE.equals(clientProperties.getS3PathStyle()))
                        .build())
                .httpClientBuilder(httpClientBuilder)
                .overrideConfiguration(cfg -> cfg
                        .retryStrategy(RetryMode.STANDARD)
                        .apiCallAttemptTimeout(Duration.ofSeconds(30))
                        .apiCallTimeout(Duration.ofSeconds(60)));

        // 使用默认凭证链 - 简化配置，支持环境变量、IAM角色等
        builder.credentialsProvider(DefaultCredentialsProvider.builder().build());

        if (clientProperties.getS3Endpoint() != null && !clientProperties.getS3Endpoint().isBlank()) {
            builder.endpointOverride(URI.create(clientProperties.getS3Endpoint()));
        }

        this.s3Client = builder.build();
        log.info("S3 客户端初始化完成，region={}, endpoint={}", clientProperties.getS3Region(), clientProperties.getS3Endpoint());
    }
    
    /**
     * 上传文件到 S3。
     */
    public boolean uploadFile(FileUploadRequest request) {
        return RetryTemplate.executeWithRetry(() -> {
            try {
                if (request.getCompressedPayload() == null || request.getCompressedPayload().length == 0) {
                    log.warn("上传负载为空，跳过: {}", request.getFilePath());
                    return false;
                }

                String key = generateS3Key(request);
                long contentLength = request.getCompressedPayload().length;

                // 小文件：单次上传
                if (contentLength <= partSizeBytes) {
                    PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentLength(contentLength)
                            .contentType("application/zip")
                            .build();

                    s3Client.putObject(putObjectRequest, RequestBody.fromBytes(request.getCompressedPayload()));
                    log.info("文件上传到S3成功: bucket={}, key={} (单次)", bucketName, key);
                    return true;
                }

                // 大文件：分片上传
                CreateMultipartUploadRequest createReq = CreateMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType("application/zip")
                        .build();
                CreateMultipartUploadResponse createRes = s3Client.createMultipartUpload(createReq);
                String uploadId = createRes.uploadId();
                List<CompletedPart> parts = new ArrayList<>();
                int partNumber = 1;
                long uploaded = 0L;

                byte[] payload = request.getCompressedPayload();
                for (int offset = 0; offset < payload.length; offset += partSizeBytes) {
                    int size = (int) Math.min(partSizeBytes, payload.length - offset);
                    UploadPartRequest partReq = UploadPartRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .uploadId(uploadId)
                            .partNumber(partNumber)
                            .contentLength((long) size)
                            .build();

                    String eTag = null;
                    for (int attempt = 1; attempt <= maxRetries; attempt++) {
                        try {
                            UploadPartResponse partRes = s3Client.uploadPart(partReq,
                                    RequestBody.fromBytes(slice(payload, offset, size)));
                            eTag = partRes.eTag();
                            break;
                        } catch (S3Exception ex) {
                            log.warn("分片上传失败，part={} attempt={}/{}: {}", partNumber, attempt, maxRetries, ex.getMessage());
                            if (attempt == maxRetries) {
                                // 终止上传
                                try {
                                    s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                                            .bucket(bucketName)
                                            .key(key)
                                            .uploadId(uploadId)
                                            .build());
                                } catch (Exception abortEx) {
                                    log.warn("中止分片上传失败: {}", abortEx.getMessage());
                                }
                                throw ex;
                            }
                        }
                    }

                    parts.add(CompletedPart.builder().partNumber(partNumber).eTag(eTag).build());
                    uploaded += size;
                    double percent = uploaded * 100.0 / contentLength;
                    log.info("上传进度: {} / {} bytes ({:.2f}%)", uploaded, contentLength, percent);
                    partNumber++;
                }

                CompletedMultipartUpload cmp = CompletedMultipartUpload.builder().parts(parts).build();
                s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .uploadId(uploadId)
                        .multipartUpload(cmp)
                        .build());
                log.info("文件上传到S3成功: bucket={}, key={} (分片)", bucketName, key);
                return true;
            } catch (Exception e) {
                log.error("上传文件到S3失败", e);
                throw new RuntimeException("S3上传失败", e);
            }
        }, 3); // 最多重试3次
    }
    
    /**
     * 从 S3 下载文件到本地。
     */
    public boolean downloadFile(String key, Path target) {
        return RetryTemplate.executeWithRetry(() -> {
            try {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();
                
                ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(getObjectRequest);
                
                // 将文件写入目标路径
                Files.write(target, inputStream.readAllBytes(), StandardOpenOption.CREATE, 
                        StandardOpenOption.TRUNCATE_EXISTING);
                
                log.info("从S3下载文件成功: bucket={}, key={}, target={}", bucketName, key, target);
                return true;
            } catch (Exception e) {
                log.error("从S3下载文件失败: key={}", key, e);
                throw new RuntimeException("S3下载失败", e);
            }
        }, 3); // 最多重试3次
    }
    
    /**
     * 删除 S3 中的对象。
     */
    public boolean deleteFile(String key) {
        return RetryTemplate.executeWithRetry(() -> {
            try {
                DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();
                
                s3Client.deleteObject(deleteObjectRequest);
                
                log.info("从S3删除文件成功: bucket={}, key={}", bucketName, key);
                return true;
            } catch (Exception e) {
                log.error("从S3删除文件失败: key={}", key, e);
                throw new RuntimeException("S3删除失败", e);
            }
        }, 3); // 最多重试3次
    }
    
    /**
     * 列出 S3 中的所有对象键。
     */
    public List<String> listFiles() {
        return RetryTemplate.executeWithRetry(() -> {
            try {
                ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .build();
                
                ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
                
                List<String> keys = new ArrayList<>();
                for (S3Object s3Object : listResponse.contents()) {
                    keys.add(s3Object.key());
                }
                
                log.debug("从S3获取文件列表成功，共{}个文件", keys.size());
                return keys;
            } catch (Exception e) {
                log.error("从S3获取文件列表失败", e);
                throw new RuntimeException("S3列出文件失败", e);
            }
        }, 3); // 最多重试3次
    }
    
    /**
     * 根据内容哈希生成唯一的 S3 对象键。
     */
    private String generateS3Key(FileUploadRequest request) {
        String contentHash = request.getContentHash();
        String fileName = request.getLocalPath().getFileName().toString();
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = fileName.substring(dotIndex);
        }
        // 使用内容哈希作为S3 key，确保相同内容有相同的key
        return "files/" + contentHash + extension;
    }

    private static byte[] slice(byte[] data, int offset, int size) {
        byte[] out = new byte[size];
        System.arraycopy(data, offset, out, 0, size);
        return out;
    }
}