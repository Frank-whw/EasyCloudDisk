package com.clouddisk.client.model;

import lombok.Data;

@Data
public class AuthResponse {
    private String userId;
    private String email;
    private String message;
    private String token;
    private String refreshToken;
}