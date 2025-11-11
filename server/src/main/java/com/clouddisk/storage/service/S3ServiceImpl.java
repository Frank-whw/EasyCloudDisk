package com.clouddisk.storage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3ServiceImpl implements S3Service {
    
    private final S3Client s3Client;
    
    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    
    @Value("${aws.s3.multipart.part-size-mb:8}")
    private int partSizeMb;

    @Value("${aws.s3.multipart.max-retries:3}")
    private int maxRetries;
    
    @Override
    public String uploadFile(MultipartFile file, String s3Key) {
        log.info("上传文件到S3，bucket: {}, key: {}", bucketName, s3Key);
        
        // 处理可能为null的内容类型，设置为默认值
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        long contentLength = file.getSize();
        long partSizeBytes = Math.max(5L * 1024 * 1024, partSizeMb * 1024L * 1024L); // S3 限制单片至少 5MB

        // 小文件直接单次上传（使用流，避免一次性加载到内存）
        if (contentLength > 0 && contentLength <= partSizeBytes) {
            try (InputStream is = file.getInputStream()) {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(is, contentLength));
                log.info("文件单次上传成功，bucket: {}, key: {}", bucketName, s3Key);
                return s3Key;
            } catch (IOException | S3Exception e) {
                log.error("文件单次上传到S3失败，key: {}", s3Key, e);
                throw new RuntimeException("文件上传失败: " + e.getMessage());
            }
        }

        // 大文件使用分片上传（流式读取，内存占用小）
        String uploadId = null;
        try (InputStream is = file.getInputStream()) {
            CreateMultipartUploadRequest createReq = CreateMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(contentType)
                    .build();

            CreateMultipartUploadResponse createRes = s3Client.createMultipartUpload(createReq);
            uploadId = createRes.uploadId();
            log.info("已创建分片上传，uploadId: {}", uploadId);

            List<CompletedPart> completedParts = new ArrayList<>();
            byte[] buffer = new byte[(int) partSizeBytes];
            int partNumber = 1;
            long uploadedBytes = 0L;
            
            while (true) {
                int bytesRead = readFully(is, buffer);
                if (bytesRead <= 0) break;

                UploadPartRequest partReq = UploadPartRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) bytesRead)
                        .build();

                // 手动重试上传分片
                String eTag = null;
                int attempt = 0;
                while (attempt < maxRetries) {
                    attempt++;
                    try {
                        UploadPartResponse partRes = s3Client.uploadPart(partReq, RequestBody.fromBytes(slice(buffer, bytesRead)));
                        eTag = partRes.eTag();
                        break;
                    } catch (S3Exception ex) {
                        log.warn("上传分片失败，part={} attempt={}/{}: {}", partNumber, attempt, maxRetries, ex.getMessage());
                        // 简单退避等待
                        try { TimeUnit.SECONDS.sleep(1L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        if (attempt >= maxRetries) throw ex;
                    }
                }

                completedParts.add(CompletedPart.builder().partNumber(partNumber).eTag(eTag).build());
                uploadedBytes += bytesRead;
                partNumber++;

                // 进度日志（如果无法获取总长度则仅记录已上传字节）
                if (contentLength > 0) {
                    double percent = uploadedBytes * 100.0 / contentLength;
                    log.info("分片上传进度: {} / {} bytes ({:.2f}%)", uploadedBytes, contentLength, percent);
                } else {
                    log.info("分片上传进度: {} bytes 已上传", uploadedBytes);
                }
            }

            CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build();

            CompleteMultipartUploadRequest completeReq = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .uploadId(uploadId)
                    .multipartUpload(completedMultipartUpload)
                    .build();

            s3Client.completeMultipartUpload(completeReq);
            log.info("文件分片上传完成，bucket: {}, key: {}", bucketName, s3Key);
            return s3Key;
        } catch (IOException | S3Exception e) {
            log.error("文件分片上传到S3失败，key: {}", s3Key, e);
            // 发生异常则中止分片上传
            if (uploadId != null) {
                try {
                    AbortMultipartUploadRequest abortReq = AbortMultipartUploadRequest.builder()
                            .bucket(bucketName)
                            .key(s3Key)
                            .uploadId(uploadId)
                            .build();
                    s3Client.abortMultipartUpload(abortReq);
                    log.warn("已中止分片上传，uploadId: {}", uploadId);
                } catch (Exception ex) {
                    log.warn("中止分片上传失败，uploadId: {}: {}", uploadId, ex.getMessage());
                }
            }
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
    public InputStream downloadFileStream(String s3Key) {
        try {
            log.info("从S3以流式方式下载文件，bucket: {}, key: {}", bucketName, s3Key);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(getObjectRequest);
            return stream;
        } catch (S3Exception e) {
            log.error("从S3流式下载文件失败，key: {}", s3Key, e);
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

    /**
     * 读取输入流至缓冲区，返回本次读取的字节数；到达 EOF 返回 -1。
     */
    private static int readFully(InputStream is, byte[] buffer) throws IOException {
        int offset = 0;
        int remaining = buffer.length;
        while (remaining > 0) {
            int read = is.read(buffer, offset, remaining);
            if (read < 0) break;
            offset += read;
            remaining -= read;
            // 若本次读取到的字节数小于缓冲区，则可能是最后一片
            if (read == 0) break;
            // 为了尽快上传片段，若可读数据不足缓冲区，退出当前循环
            if (is.available() == 0) break;
        }
        return offset == 0 ? -1 : offset;
    }

    /**
     * 返回 buffer 的前 size 字节的副本，避免上传多余字节。
     */
    private static byte[] slice(byte[] buffer, int size) {
        if (size == buffer.length) return buffer;
        byte[] out = new byte[size];
        System.arraycopy(buffer, 0, out, 0, size);
        return out;
    }
}