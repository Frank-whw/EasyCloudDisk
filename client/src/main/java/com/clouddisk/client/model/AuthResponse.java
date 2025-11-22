package com.clouddisk.client.model;

import lombok.Data;

/**
 * 服务端返回的认证响应信息，包含访问令牌及用户元数据。
 */
@Data
public class AuthResponse {
    private String userId;
    private String email;
    private String message;
    private String token;
    private String refreshToken;
}