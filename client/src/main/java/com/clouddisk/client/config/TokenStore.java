package com.clouddisk.client.config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 简单的令牌存储器，负责在本地缓存登录后的访问令牌。
 * <p>
 * 令牌既保存在内存中，应用重启时也会尝试从磁盘文件中恢复，实现最小化的持久化能力。
 */
public class TokenStore {
    private static final String TOKEN_FILE = ".token";
    private String token;

    /**
     * 保存令牌。
     *
     * @param token 需要持久化的访问令牌。
     */
    public void saveToken(String token) {
        this.token = token;
        // 同时保存到文件中，实现持久化存储
        saveTokenToFile(token);
    }
    /**
     * 获取令牌。
     *
     * @return 若存在则返回内存或磁盘中的访问令牌，否则为 {@code null}。
     */
    public String getToken() {
        // 如果内存中没有token，则从文件中读取
        if (token == null) {
            token = readTokenFromFile();
        }
        return token;
    }
    /**
     * 保存令牌到磁盘文件。
     */
    private void saveTokenToFile(String token) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(TOKEN_FILE))) {
            writer.print(token);
        } catch (IOException e) {
            // 如果保存失败，仅在内存中保存
            e.printStackTrace();
        }
    }
    /**
     * 从磁盘文件中读取令牌。
     */
    private String readTokenFromFile() {
        try {
            Path path = Paths.get(TOKEN_FILE);
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path));
            }
        } catch (IOException e) {
            // 如果读取失败，返回null
            e.printStackTrace();
        }
        return null;
    }
}