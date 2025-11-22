package com.clouddisk.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.clouddisk.config.OssProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Alibaba Cloud OSS storage service implementation.
 */
@Service
public class OssStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(OssStorageService.class);

    @Autowired
    private OSS ossClient;

    @Autowired
    private OssProperties ossProperties;

    @Override
    public String storeFile(MultipartFile file, String keyPrefix, boolean compress) {
        try {
            String key = keyPrefix + "/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();

            // For now, store without compression
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                ossProperties.getBucketName(),
                key,
                file.getInputStream()
            );

            ossClient.putObject(putObjectRequest);
            log.info("File stored successfully: {}", key);
            return key;
        } catch (IOException e) {
            log.error("Failed to store file", e);
            throw new RuntimeException("OSS 文件上传失败", e);
        }
    }

    @Override
    public String storeBytes(byte[] data, String keyPrefix, String filename, boolean alreadyCompressed) {
        String key = keyPrefix + "/" + filename;

        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                ossProperties.getBucketName(),
                key,
                new ByteArrayInputStream(data)
            );

            ossClient.putObject(putObjectRequest);
            log.info("Bytes stored successfully: {}", key);
            return key;
        } catch (Exception e) {
            log.error("Failed to store bytes", e);
            throw new RuntimeException("OSS 字节上传失败", e);
        }
    }

    @Override
    public InputStream loadFile(String storageKey, boolean decompress) {
        try {
            OSSObject ossObject = ossClient.getObject(ossProperties.getBucketName(), storageKey);
            log.info("File loaded successfully: {}", storageKey);
            return ossObject.getObjectContent();
        } catch (OSSException e) {
            log.error("Failed to load file: {}", storageKey, e);
            throw new RuntimeException("OSS 文件加载失败", e);
        }
    }

    @Override
    public void deleteFile(String storageKey) {
        try {
            ossClient.deleteObject(ossProperties.getBucketName(), storageKey);
            log.info("File deleted successfully: {}", storageKey);
        } catch (Exception e) {
            log.error("Failed to delete file: {}", storageKey, e);
            throw new RuntimeException("OSS 文件删除失败", e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            return ossClient.doesObjectExist(ossProperties.getBucketName(), storageKey);
        } catch (Exception e) {
            log.error("Failed to check if file exists: {}", storageKey, e);
            return false;
        }
    }

    @Override
    public void ensureBucket() {
        try {
            if (!ossClient.doesBucketExist(ossProperties.getBucketName())) {
                ossClient.createBucket(ossProperties.getBucketName());
                log.info("Bucket created: {}", ossProperties.getBucketName());
            } else {
                log.info("Bucket already exists: {}", ossProperties.getBucketName());
            }
        } catch (Exception e) {
            log.error("Failed to ensure bucket exists", e);
            throw new RuntimeException("OSS 存储桶检查失败", e);
        }
    }

    @Override
    public boolean isHealthy() {
        System.out.println("OSS health check start, bucket: " + ossProperties.getBucketName());
        try {
            boolean exists = ossClient.doesBucketExist(ossProperties.getBucketName());
            System.out.println("OSS bucket exists: " + exists);
            if (!exists) {
                log.warn("OSS bucket does not exist: {}", ossProperties.getBucketName());
            }
            return exists;
        } catch (Exception e) {
            System.out.println("OSS health check failed: " + e.getMessage());
            log.error("OSS health check failed for bucket: {}", ossProperties.getBucketName(), e);
            return false;
        }
    }
}