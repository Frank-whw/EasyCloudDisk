package com.clouddisk.client.sync;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 哈希计算器，提供整文件与分块哈希功能。
 */
@Slf4j
public class HashCalculator {

    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;
    private int chunkSizeBytes = 1_048_576;

    public HashCalculator() {
    }

    public HashCalculator(int chunkSizeBytes) {
        setChunkSizeBytes(chunkSizeBytes);
    }

    public void setChunkSizeBytes(int chunkSizeBytes) {
        if (chunkSizeBytes <= 0) {
            throw new IllegalArgumentException("chunkSizeBytes must be > 0");
        }
        this.chunkSizeBytes = chunkSizeBytes;
    }

    public int getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    /**
     * 计算文件整体哈希。
     */
    public String computeFileHash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(path)) {
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return bytesToHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("计算文件哈希值失败: {}", path, e);
            return null;
        }
    }

    /**
     * 计算文件分块哈希。
     */
    public Map<Integer, String> chunkHashes(Path path) {
        Map<Integer, String> result = new LinkedHashMap<>();
        try (InputStream inputStream = Files.newInputStream(path)) {
            byte[] buffer = new byte[chunkSizeBytes];
            int bytesRead;
            int index = 0;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(buffer, 0, bytesRead);
                result.put(index++, bytesToHex(digest.digest()));
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("计算文件块哈希值失败: {}", path, e);
            return new LinkedHashMap<>();
        }
        return result;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}