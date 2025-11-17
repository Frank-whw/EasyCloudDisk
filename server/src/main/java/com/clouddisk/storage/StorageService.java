package com.clouddisk.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 抽象的文件存储服务接口。
 */
public interface StorageService {
    String storeFile(MultipartFile file, String keyPrefix, boolean compress);

    /**
     * 直接存储字节数组(用于块级存储)。
     */
    String storeBytes(byte[] data, String keyPrefix, String filename, boolean alreadyCompressed);

    InputStream loadFile(String storageKey, boolean decompress);

    void deleteFile(String storageKey);

    boolean exists(String storageKey);

    void ensureBucket();

    boolean isHealthy();
}
