package com.platform.iam.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token 服务
 */
@Component
public class JwtTokenService {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration:86400000}") long accessTokenExpiration,
            @Value("${app.jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * 生成访问令牌
     */
    public String generateAccessToken(String userId, String tenantId, String username, String[] roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpiration);

        return Jwts.builder()
            .subject(userId)
            .claim("tenant_id", tenantId)
            .claim("username", username)
            .claim("roles", roles)
            .claim("type", "access")
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .id(UUID.randomUUID().toString())
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact();
    }

    /**
     * 生成刷新令牌
     */
    public String generateRefreshToken(String userId, String tenantId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshTokenExpiration);

        return Jwts.builder()
            .subject(userId)
            .claim("tenant_id", tenantId)
            .claim("type", "refresh")
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .id(UUID.randomUUID().toString())
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact();
    }

    /**
     * 解析并验证令牌
     */
    public Jws<Claims> parseToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token);
    }

    /**
     * 获取用户ID
     */
    public String getUserId(String token) {
        return parseToken(token).getPayload().getSubject();
    }

    /**
     * 获取租户ID
     */
    public String getTenantId(String token) {
        return parseToken(token).getPayload().get("tenant_id", String.class);
    }

    /**
     * 获取用户名
     */
    public String getUsername(String token) {
        return parseToken(token).getPayload().get("username", String.class);
    }

    /**
     * 获取角色列表
     */
    public String[] getRoles(String token) {
        Object roles = parseToken(token).getPayload().get("roles");
        if (roles instanceof String[]) {
            return (String[]) roles;
        }
        return new String[0];
    }

    /**
     * 获取令牌类型
     */
    public String getTokenType(String token) {
        return parseToken(token).getPayload().get("type", String.class);
    }

    /**
     * 检查令牌是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            return parseToken(token).getPayload().getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * 验证令牌
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return !isTokenExpired(token);
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * 获取访问令牌过期时间（毫秒）
     */
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    /**
     * 获取刷新令牌过期时间（毫秒）
     */
    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}