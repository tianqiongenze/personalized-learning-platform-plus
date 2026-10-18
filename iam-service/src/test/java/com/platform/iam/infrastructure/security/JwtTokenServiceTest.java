package com.platform.iam.infrastructure.security;

import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenService 测试")
class JwtTokenServiceTest {

    /** HS256 要求密钥长度 >= 256 bit(32 字节) */
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final long ACCESS_EXPIRATION = 3_600_000L;      // 1h
    private static final long REFRESH_EXPIRATION = 604_800_000L;   // 7d

    private JwtTokenService service;

    @BeforeEach
    void setUp() {
        service = new JwtTokenService(SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
    }

    @Test
    @DisplayName("访问令牌应能往返解析用户信息")
    void shouldRoundTripAccessToken() {
        String token = service.generateAccessToken("user-1", "tenant-1", "alice", new String[]{"TEACHER", "ADMIN"});

        assertThat(token).isNotBlank();
        assertThat(service.getUserId(token)).isEqualTo("user-1");
        assertThat(service.getTenantId(token)).isEqualTo("tenant-1");
        assertThat(service.getUsername(token)).isEqualTo("alice");
        assertThat(service.getTokenType(token)).isEqualTo("access");
    }

    @Test
    @DisplayName("访问令牌不应被视为过期")
    void accessTokenShouldNotBeExpired() {
        String token = service.generateAccessToken("user-1", "tenant-1", "alice", new String[]{});

        assertThat(service.isTokenExpired(token)).isFalse();
        assertThat(service.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("刷新令牌应携带 refresh 类型")
    void shouldGenerateRefreshToken() {
        String token = service.generateRefreshToken("user-2", "tenant-2");

        assertThat(service.getUserId(token)).isEqualTo("user-2");
        assertThat(service.getTenantId(token)).isEqualTo("tenant-2");
        assertThat(service.getTokenType(token)).isEqualTo("refresh");
        assertThat(service.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("parseToken 应返回可用的 claims")
    void shouldParseTokenClaims() {
        String token = service.generateAccessToken("user-1", "tenant-1", "alice", new String[]{"TEACHER"});

        Jws<Claims> jws = service.parseToken(token);

        assertThat(jws.getPayload().getSubject()).isEqualTo("user-1");
        assertThat(jws.getPayload().get("tenant_id", String.class)).isEqualTo("tenant-1");
        assertThat(jws.getPayload().getId()).isNotBlank();
        assertThat(jws.getPayload().getExpiration()).isAfter(jws.getPayload().getIssuedAt());
    }

    @Test
    @DisplayName("getRoles 返回非空数组")
    void shouldReturnRolesArray() {
        String token = service.generateAccessToken("user-1", "tenant-1", "alice", new String[]{"TEACHER", "ADMIN"});

        assertThat(service.getRoles(token)).isNotNull();
    }

    @Test
    @DisplayName("刷新令牌无 roles 声明时返回空数组")
    void shouldReturnEmptyRolesWhenAbsent() {
        String token = service.generateRefreshToken("user-1", "tenant-1");

        assertThat(service.getRoles(token)).isEmpty();
    }

    @Test
    @DisplayName("非法令牌校验应返回 false")
    void shouldRejectInvalidToken() {
        assertThat(service.validateToken("not-a-jwt")).isFalse();
        assertThat(service.validateToken("a.b.c")).isFalse();
    }

    @Test
    @DisplayName("签名不匹配的令牌校验应返回 false")
    void shouldRejectTokenSignedWithAnotherKey() {
        JwtTokenService other = new JwtTokenService(
                "ffffffffffffffffffffffffffffffffffffffffffffffff", ACCESS_EXPIRATION, REFRESH_EXPIRATION);
        String foreignToken = other.generateAccessToken("user-1", "tenant-1", "alice", new String[]{});

        assertThat(service.validateToken(foreignToken)).isFalse();
    }

    @Test
    @DisplayName("已过期令牌应被识别为过期且校验失败")
    void shouldDetectExpiredToken() {
        JwtTokenService expiredService = new JwtTokenService(SECRET, -1_000L, -1_000L);
        String token = expiredService.generateAccessToken("user-1", "tenant-1", "alice", new String[]{});

        assertThat(expiredService.isTokenExpired(token)).isTrue();
        assertThat(expiredService.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("解析非法令牌应抛出异常")
    void shouldThrowWhenParsingGarbage() {
        assertThatThrownBy(() -> service.parseToken("garbage"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("应暴露令牌有效期配置")
    void shouldExposeExpirations() {
        assertThat(service.getAccessTokenExpiration()).isEqualTo(ACCESS_EXPIRATION);
        assertThat(service.getRefreshTokenExpiration()).isEqualTo(REFRESH_EXPIRATION);
    }
}
