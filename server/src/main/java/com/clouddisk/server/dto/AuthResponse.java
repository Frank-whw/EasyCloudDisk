package com.clouddisk.server.dto;

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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
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

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
