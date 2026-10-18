package com.platform.iam.interfaces.rest;

import com.platform.iam.application.command.*;
import com.platform.iam.application.query.*;
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
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证授权", description = "登录、令牌刷新、密码管理等认证相关 API")
public class AuthController {

    private final UserApplicationService userService;
    private final JwtTokenService jwtTokenService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户名密码登录，返回访问令牌和刷新令牌")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        // 实际应调用认证服务验证用户名密码
        // 这里简化处理，实际应注入 AuthenticationManager
        throw new UnsupportedOperationException("需集成 Spring Security AuthenticationManager");
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新访问令牌", description = "使用刷新令牌获取新的访问令牌")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        // 验证刷新令牌并生成新的访问令牌
        throw new UnsupportedOperationException("需实现刷新令牌逻辑");
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
