package com.clouddisk.client.sync;

import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * 哈希计算器
 * 用于计算文件的哈希值，支持整体文件哈希和分块哈希计算
 */
@Slf4j
public class HashCalculator {
    
    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB
    
    /**
     * 计算文件哈希值
     * @param path 文件路径
     * @return 文件哈希值（SHA-256）
     */
    public String computeFileHash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("计算文件哈希值失败: {}", path, e);
            return null;
        }
    }
    
    /**
     * 计算文件块哈希值
     * @param path 文件路径
     * @return 块哈希值映射，键为块索引，值为块的哈希值
     */
    public Map<Integer, String> chunkHashes(Path path) {
        Map<Integer, String> chunkHashes = new HashMap<>();
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;
                int chunkIndex = 0;
                
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                    byte[] hashBytes = digest.digest();
                    chunkHashes.put(chunkIndex, bytesToHex(hashBytes));
                    
                    // 重置摘要器以计算下一个块
                    digest.reset();
                    chunkIndex++;
                }
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("计算文件块哈希值失败: {}", path, e);
            return new HashMap<>();
        }
        
        return chunkHashes;
    }
    
    /**
     * 将字节数组转换为十六进制字符串
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}