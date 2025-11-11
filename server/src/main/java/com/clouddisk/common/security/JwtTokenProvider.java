package com.clouddisk.common.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 令牌提供者
 * 负责生成和验证JWT令牌
 */
@Component // 将类注册为组件
public class JwtTokenProvider {
    private final SecretKey secretKey;
    private final long jwtExpiration;
    // 从配置文件中获取密钥和过期时间
    public JwtTokenProvider(@Value("${jwt.secret}") String jwtSecret,
                            @Value("${jwt.expiration}") long jwtExpiration) {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("JWT密钥未配置");
        }
        // 基本强度校验：至少32字节，避免占位符/弱密钥
        if (jwtSecret.length() < 32 || jwtSecret.toLowerCase().startsWith("your-secret")) {
            throw new IllegalStateException("JWT密钥强度不足，请使用至少256位随机密钥");
        }
        try {
            this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT密钥无效或长度不足: " + e.getMessage());
        }
        if (jwtExpiration <= 0) {
            throw new IllegalStateException("JWT过期时间必须为正数");
        }
        this.jwtExpiration = jwtExpiration;
    }
    /**
     * 生成JWT令牌
     * @param userId 用户ID
     * @return JWT令牌
     */
    public String generateToken(String userId) {
        // 1. 设置令牌主题为用户ID
        // 2. 设置发行时间和过期时间
        // 3. 使用密钥签名
        // 4. 返回令牌字符串
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从JWT令牌中获取用户ID
     * @param token JWT令牌
     * @return 用户ID
     */
    public String getUserIdFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    /**
     * 验证JWT令牌
     * @param token JWT令牌
     * @return 验证结果
     */
    public boolean validateToken(String token) {
        try {
            return getClaimsFromToken(token) != null && !isTokenExpired(token);
        } catch (JwtException e) {
            // 记录具体的验证失败原因
            System.err.println("JWT验证失败: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("JWT验证发生未知错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 验证JWT令牌是否过期
     * @param token JWT令牌
     * @return 是否过期
     */
    private boolean isTokenExpired(String token) {
        Date expiration = getExpirationDateFromToken(token);
        return expiration != null && expiration.before(new Date());
    }


    /**
     * 获取JWT令牌的过期时间
     * @param token JWT令牌
     * @return 过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getExpiration() : null;
    }

    /**
     * 获取JWT令牌的Claims
     * @param token JWT令牌
     * @return Claims对象，如果解析失败返回null
     */
    private Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            System.err.println("JWT解析失败: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("JWT解析发生未知错误: " + e.getMessage());
            return null;
        }
    }



}
