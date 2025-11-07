package com.clouddisk.client.config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TokenStore {
    private static final String TOKEN_FILE = ".token";
    private String token;

    /**
     * 保存令牌
     * @param token 令牌
     */
    public void saveToken(String token) {
        this.token = token;
        // 同时保存到文件中，实现持久化存储
        saveTokenToFile(token);
    }
    /**
     * 获取令牌
     * @return 令牌
     */
    public String getToken() {
        // 如果内存中没有token，则从文件中读取
        if (token == null) {
            token = readTokenFromFile();
        }
        return token;
    }
    /**
     * 保存token到文件中
     * @param token 令牌
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
     * 从文件中读取token
     * @return 令牌
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