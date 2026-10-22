package com.platform.iam.interfaces.rest;

import com.platform.iam.application.command.*;
import com.platform.iam.application.query.*;
import com.platform.iam.application.service.AuthApplicationService;
import com.platform.iam.application.service.UserApplicationService;
import com.platform.iam.application.service.RoleApplicationService;
import com.platform.iam.domain.model.valueobjects.*;
import com.platform.iam.interfaces.rest.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;
import com.platform.iam.infrastructure.security.JwtTokenService;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "认证授权", description = "登录、令牌刷新、密码管理等认证相关 API")
public class AuthController {

    private final UserApplicationService userService;
    private final AuthApplicationService authService;
    private final JwtTokenService jwtTokenService;
    private final RoleApplicationService roleService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户名密码登录，返回访问令牌和刷新令牌")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        // 真实认证: BCrypt 校验 + 账户状态判定 + 登录记录(成功/失败计数与自动锁定)
        String clientIp = httpRequest.getRemoteAddr();
        String tenantId = httpRequest.getHeader("X-Tenant-Id");
        var result = authService.authenticate(
                com.platform.iam.domain.model.valueobjects.TenantId.of(
                        tenantId == null || tenantId.isBlank() ? "00000000-0000-0000-0000-000000000000" : tenantId),
                request.username(), request.password(), clientIp, httpRequest.getHeader("User-Agent"));
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(result.failureReason()));
        }
        var user = result.user();
        // 角色写角色码(ADMIN/TEACHER...)而非 UUID: 网关/下游 hasRole('XXX') 才能匹配
        String[] roles = roleService.findByUserId(user.getId()).stream()
                .map(r -> r.code()).toArray(String[]::new);
        String access = jwtTokenService.generateAccessToken(
                user.getId().getValue().toString(), user.getTenantId().getValue().toString(),
                user.getUsername(), roles);
        String refresh = jwtTokenService.generateRefreshToken(
                user.getId().getValue().toString(), user.getTenantId().getValue().toString(),
                user.getUsername(), roles);
        return ResponseEntity.ok(ApiResponse.success(new LoginResponse(
                access, refresh, 900, "Bearer",
                new com.platform.iam.interfaces.rest.dto.UserInfo(user.getId().getValue().toString(),
                        user.getTenantId().getValue().toString(), user.getUsername(),
                        user.getEmail(), user.getPhone(), roles))));
    }

    /**
     * D-05/D-06 网关鉴权子请求: 校验 Bearer JWT, 有效则回显 200 +
     * X-User-Id / X-User-Roles 响应头, 供 nginx auth_request 捕获注入下游。
     * 无/无效 token 返回 401, nginx 即拒绝上游请求。
     */
    @RequestMapping(value="/verify", method={org.springframework.web.bind.annotation.RequestMethod.GET,
            org.springframework.web.bind.annotation.RequestMethod.POST})
    @Operation(summary = "网关鉴权子请求", description = "校验 JWT 并回显用户身份头")
    public ResponseEntity<Void> verify(jakarta.servlet.http.HttpServletRequest httpRequest) {
        String auth = httpRequest.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = auth.substring(7).trim();
        try {
            String[] roles = jwtTokenService.getRoles(token);
            if (!"access".equals(jwtTokenService.getTokenType(token)) || roles.length == 0) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            return ResponseEntity.ok()
                    .header("X-User-Id", jwtTokenService.getUserId(token))
                    .header("X-User-Roles", String.join(",", roles))
                    .build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新访问令牌", description = "使用刷新令牌获取新的访问令牌")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        // 真实刷新: 校验 refresh token → 重签 access + 同族 refresh
        try {
            var claims = jwtTokenService.parseToken(request.refreshToken());
            if (!"refresh".equals(jwtTokenService.getTokenType(request.refreshToken()))) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("非刷新令牌"));
            }
            String userId = jwtTokenService.getUserId(request.refreshToken());
            String tenantId = jwtTokenService.getTenantId(request.refreshToken());
            String username = jwtTokenService.getUsername(request.refreshToken());
            String[] roles = jwtTokenService.getRoles(request.refreshToken());
            String access = jwtTokenService.generateAccessToken(userId, tenantId, username, roles);
            String refresh = jwtTokenService.generateRefreshToken(userId, tenantId, username, roles);
            return ResponseEntity.ok(ApiResponse.success(new LoginResponse(
                    access, refresh, 900, "Bearer",
                    new com.platform.iam.interfaces.rest.dto.UserInfo(userId, tenantId, username, null, null, roles))));
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("刷新令牌无效或已过期"));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "使当前令牌失效（需配合 Redis 黑名单）")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout() {
        // 实际应将令牌加入黑名单
        return ResponseEntity.ok(ApiResponse.success("登出成功", null));
    }

    @PostMapping("/change-password")
    @Operation(summary = "修改密码", description = "用户修改自己的密码")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(new ChangePasswordCommand(
            UserId.of(userId), request.oldPassword(), request.newPassword(), userId
        ));
        return ResponseEntity.ok(ApiResponse.success("密码修改成功", null));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "忘记密码", description = "发送重置密码邮件/短信")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestParam String email) {
        // 实际应发送重置密码链接
        return ResponseEntity.ok(ApiResponse.success("重置密码链接已发送", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "重置密码", description = "通过重置链接设置新密码")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestParam String token, @Valid @RequestBody ResetPasswordRequest request) {
        // 实际应验证 token 并重置密码
        return ResponseEntity.ok(ApiResponse.success("密码重置成功", null));
    }
}
