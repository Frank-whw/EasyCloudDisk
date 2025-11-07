package com.clouddisk.server.security;


import io.jsonwebtoken.Claims;
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
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
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
        } catch (Exception e) {
            // 记录具体的验证失败原因
            System.err.println("JWT验证失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 验证JWT令牌是否过期
     * @param token JWT令牌
     * @return 是否过期
     */
    public boolean isTokenExpired(String token) {
        return getExpirationDateFromToken(token).before(new Date());
    }


    /**
     * 获取JWT令牌的过期时间
     * @param token JWT令牌
     * @return 过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * 检查JWT令牌是否过期
     * @param token JWT令牌
     * @return 是否过期
     */
    private boolean isTokenExpired(String token) {
        Date expiration = getExpirationDateFromToken(token);
        return expiration != null && expiration.before(new Date());
    }

    /**
     * 获取JWT令牌的Claims
     * @param token JWT令牌
     * @return Claims对象，如果解析失败返回null
     */
    private Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(jwtSecret)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (SignatureException e) {
            System.err.println("JWT签名验证失败: " + e.getMessage());
            return null;
        } catch (MalformedJwtException e) {
            System.err.println("JWT格式错误: " + e.getMessage());
            return null;
        } catch (ExpiredJwtException e) {
            System.err.println("JWT已过期: " + e.getMessage());
            return null;
        } catch (UnsupportedJwtException e) {
            System.err.println("不支持的JWT: " + e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            System.err.println("JWT参数非法: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("JWT解析失败: " + e.getMessage());
            return null;
        }
    }
    /**
     * 解析JWT令牌
     * @param token JWT令牌
     * @return Claims
     */
    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }



}
