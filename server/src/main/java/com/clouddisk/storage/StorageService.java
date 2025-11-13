package com.clouddisk.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 抽象的文件存储服务接口。
 */
public interface StorageService {
    String storeFile(MultipartFile file, String keyPrefix, boolean compress);

    InputStream loadFile(String storageKey, boolean decompress);

    void deleteFile(String storageKey);

    boolean exists(String storageKey);

    void ensureBucket();

    boolean isHealthy();
}
