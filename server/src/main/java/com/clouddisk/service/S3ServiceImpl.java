package com.clouddisk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3ServiceImpl implements S3Service {
    
    private final S3Client s3Client;
    
    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    
    @Override
    public String uploadFile(MultipartFile file, String s3Key) {
        try {
            log.info("上传文件到S3，bucket: {}, key: {}", bucketName, s3Key);
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            
            RequestBody requestBody = RequestBody.fromInputStream(
                    file.getInputStream(), file.getSize());
            
            s3Client.putObject(putObjectRequest, requestBody);
            
            log.info("文件上传成功，bucket: {}, key: {}", bucketName, s3Key);
            return s3Key;
            
        } catch (IOException e) {
            log.error("文件上传到S3失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }
    
    @Override
    public byte[] downloadFile(String s3Key) {
        try {
            log.info("从S3下载文件，bucket: {}, key: {}", bucketName, s3Key);
            
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            
            return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
            
        } catch (S3Exception e) {
            log.error("从S3下载文件失败，key: {}", s3Key, e);
            throw new RuntimeException("文件下载失败: " + e.getMessage());
        }
    }
    
    @Override
    public void deleteFile(String s3Key) {
        try {
            log.info("从S3删除文件，bucket: {}, key: {}", bucketName, s3Key);
            
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            
            s3Client.deleteObject(deleteObjectRequest);
            
            log.info("文件删除成功，bucket: {}, key: {}", bucketName, s3Key);
            
        } catch (S3Exception e) {
            log.error("从S3删除文件失败，key: {}", s3Key, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage());
        }
    }
}