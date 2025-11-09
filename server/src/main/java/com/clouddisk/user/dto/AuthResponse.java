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
    
    // 构造函数
    public AuthResponse() {}
    
    public AuthResponse(UUID userId, String email, String token) {
        this.userId = userId;
        this.email = email;
        this.token = token;
    }
    
    public AuthResponse(String token, UUID userId, String email) {
        this.token = token;
        this.userId = userId;
        this.email = email;
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
}