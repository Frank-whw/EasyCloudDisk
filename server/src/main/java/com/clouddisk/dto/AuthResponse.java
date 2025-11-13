package com.clouddisk.dto;

/**
 * 登录成功后返回的用户凭证信息。
 */
import lombok.Data;

@Data
public class AuthResponse {
    private String userId;
    private String email;
    private String token;
    private String refreshToken;

    public AuthResponse() {
    }

    public AuthResponse(String userId, String email, String token, String refreshToken) {
        this.userId = userId;
        this.email = email;
        this.token = token;
        this.refreshToken = refreshToken;
    }

}
