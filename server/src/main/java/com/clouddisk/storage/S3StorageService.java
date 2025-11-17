package com.clouddisk.storage;

import com.clouddisk.config.AwsProperties;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

/**
 * 基于 AWS S3 的文件存储实现。
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3", matchIfMissing = true)
public class S3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3Client;
    private final AwsProperties properties;

    public S3StorageService(S3Client s3Client, AwsProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /**
     * 上传文件到 S3，根据需要自动压缩并附加元数据。
     */
    @Override
    public String storeFile(MultipartFile file, String keyPrefix, boolean compress) {
        try {
            String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
            String normalizedPrefix = keyPrefix == null ? "" : keyPrefix.replaceAll("/+", "/");
            if (normalizedPrefix.startsWith("/")) {
                normalizedPrefix = normalizedPrefix.substring(1);
            }
            if (normalizedPrefix.endsWith("/")) {
                normalizedPrefix = normalizedPrefix.substring(0, normalizedPrefix.length() - 1);
            }
            String baseKey = (normalizedPrefix.isEmpty() ? "" : normalizedPrefix + "/")
                    + Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
            String storageKey = extension != null ? baseKey + "." + extension : baseKey;

            byte[] bytes = file.getBytes();
            byte[] payload = bytes;
            if (compress) {
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(bos)) {
                        gzip.write(bytes);
                    }
                    payload = bos.toByteArray();
                }
            }

            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(properties.getS3().getBucketName())
                    .key(storageKey)
                    .contentLength((long) payload.length)
                    .metadata(java.util.Map.of(
                            "original-size", String.valueOf(bytes.length),
                            "compressed", Boolean.toString(compress),
                            "sha256", DigestUtils.sha256Hex(bytes)
                    ));
            if (compress) {
                requestBuilder.contentEncoding("gzip");
            }

            s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(payload));
            return storageKey;
        } catch (IOException | AwsServiceException ex) {
            log.error("Failed to upload file to S3", ex);
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "S3 上传失败", ex);
        }
    }

    /**
     * 直接存储字节数组(用于块级存储)。
     */
    @Override
    public String storeBytes(byte[] data, String keyPrefix, String filename, boolean alreadyCompressed) {
        try {
            String normalizedPrefix = keyPrefix == null ? "" : keyPrefix.replaceAll("/+", "/");
            if (normalizedPrefix.startsWith("/")) {
                normalizedPrefix = normalizedPrefix.substring(1);
            }
            if (normalizedPrefix.endsWith("/")) {
                normalizedPrefix = normalizedPrefix.substring(0, normalizedPrefix.length() - 1);
            }
            String storageKey = (normalizedPrefix.isEmpty() ? "" : normalizedPrefix + "/") + filename;

            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(properties.getS3().getBucketName())
                    .key(storageKey)
                    .contentLength((long) data.length)
                    .metadata(java.util.Map.of(
                            "compressed", Boolean.toString(alreadyCompressed)
                    ));
            
            if (alreadyCompressed) {
                requestBuilder.contentEncoding("gzip");
            }

            s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(data));
            log.debug("Uploaded chunk to S3: {}", storageKey);
            return storageKey;
        } catch (AwsServiceException ex) {
            log.error("Failed to upload bytes to S3", ex);
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "S3 上传失败", ex);
        }
    }

    /**
     * 下载文件并根据标记选择性解压。
     */
    @Override
    public InputStream loadFile(String storageKey, boolean decompress) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(properties.getS3().getBucketName())
                    .key(storageKey)
                    .build();
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            if (decompress) {
                return new java.util.zip.GZIPInputStream(response);
            }
            return response;
        } catch (IOException | S3Exception ex) {
            log.error("Failed to download file from S3", ex);
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "S3 下载失败", ex);
        }
    }

    /**
     * 删除指定对象。
     */
    @Override
    public void deleteFile(String storageKey) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(properties.getS3().getBucketName())
                    .key(storageKey)
                    .build();
            s3Client.deleteObject(request);
        } catch (S3Exception ex) {
            log.error("Failed to delete file from S3", ex);
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "S3 删除失败", ex);
        }
    }

    /**
     * 判断对象是否存在。
     */
    @Override
    public boolean exists(String storageKey) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(properties.getS3().getBucketName())
                    .key(storageKey)
                    .build();
            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            log.error("Failed to check file existence", ex);
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "S3 查询失败", ex);
        }
    }

    /**
     * 确保存储桶存在。
     */
    @Override
    public void ensureBucket() {
        String bucketName = properties.getS3().getBucketName();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException ex) {
            log.info("Bucket {} not found, creating...", bucketName);
            CreateBucketRequest.Builder builder = CreateBucketRequest.builder().bucket(bucketName);
            if (properties.getS3().getEndpoint() == null || properties.getS3().getEndpoint().isBlank()) {
                builder.createBucketConfiguration(CreateBucketConfiguration.builder()
                        .locationConstraint(properties.getRegion())
                        .build());
            }
            s3Client.createBucket(builder.build());
        } catch (S3Exception ex) {
            log.error("Failed to ensure bucket exists", ex);
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "检查或创建存储桶失败", ex);
        }
    }

    /**
     * 简单的健康检查。
     */
    @Override
    public boolean isHealthy() {
        try {
            s3Client.listBuckets();
            return true;
        } catch (S3Exception ex) {
            log.error("S3 health check failed", ex);
            return false;
        }
    }
}
