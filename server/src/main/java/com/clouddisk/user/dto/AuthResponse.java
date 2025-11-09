package com.clouddisk.user.dto;

import java.util.UUID;

/**
 * 认证响应DTO
 * 用于返回用户认证结果
 */
public class AuthResponse {
    
    private UUID userId;
    private String email;
    private String token;
    private String username;
    
    // 构造函数
    public AuthResponse() {}
    
    public AuthResponse(UUID userId, String email, String token, String username) {
        this.userId = userId;
        this.email = email;
        this.token = token;
        this.username = username;
    }
    
    public AuthResponse(String token, UUID userId, String email) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.username = email; // 默认使用邮箱作为用户名
    }
    
    // Getter和Setter方法
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
}