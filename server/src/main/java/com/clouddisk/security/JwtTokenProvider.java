package com.clouddisk.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * JWT 令牌工具类，负责生成、解析与校验访问令牌及刷新令牌。
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:86400000}") long expirationMs
    ) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException ex) {
            keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        this.accessKey = (SecretKey) Keys.hmacShaKeyFor(keyBytes);
        this.refreshKey = (SecretKey) Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValidityMs = expirationMs;
        this.refreshTokenValidityMs = Duration.ofDays(7).toMillis();
    }

    /**
     * 生成访问令牌。
     */
    public String generateToken(String subject, Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenValidityMs)))
                .signWith(accessKey)
                .compact();
    }

    /**
     * 生成刷新令牌。
     */
    public String generateRefreshToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(refreshTokenValidityMs)))
                .signWith(refreshKey)
                .compact();
    }

    public String generateRefreshToken(String subject, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(refreshTokenValidityMs)))
                .claim("tokenVersion", tokenVersion)
                .signWith(refreshKey)
                .compact();
    }

    /**
     * 校验令牌是否有效。
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            parseRefreshClaims(token);
            return true;
        } catch (Exception ex) {
            log.warn("Invalid refresh token: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 从令牌中提取主体信息。
     */
    public Optional<String> getSubject(String token) {
        try {
            Claims claims = parseClaims(token);
            return Optional.ofNullable(claims.getSubject());
        } catch (Exception ex) {
            log.warn("Failed to extract subject from token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 解析令牌并返回 {@link Claims}。
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(accessKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims parseRefreshClaims(String token) {
        return Jwts.parser()
                .verifyWith(refreshKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从请求头中提取 JWT 字符串。
     */
    public String resolveToken(jakarta.servlet.http.HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        String headerToken = request.getHeader("X-Auth-Token");
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }
        return null;
    }
}
