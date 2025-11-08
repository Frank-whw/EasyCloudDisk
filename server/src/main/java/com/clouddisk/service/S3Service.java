package com.clouddisk.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

public interface S3Service {
    String uploadFile(MultipartFile file, String s3Key);
    byte[] downloadFile(String s3Key);
    InputStream downloadFileStream(String s3Key);
    void deleteFile(String s3Key);
}