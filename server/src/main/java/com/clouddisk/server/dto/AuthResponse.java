package com.clouddisk.server.dto;

import lombok.Data;

/**
 * 认证响应DTO
 * 返回JWT 令牌和用户信息
 */
@Data
public class AuthResponse {
    private String userId;
    private String email;
    private String message;
    private String token;
    // 构造函数
    public AuthResponse(){}
    public AuthResponse(String token, String userId, String email) {
        this.message = "认证成功";
        this.token = token;
        this.userId = userId;
        this.email = email;
    }
}
