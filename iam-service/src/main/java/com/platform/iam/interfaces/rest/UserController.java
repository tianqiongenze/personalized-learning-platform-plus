package com.platform.iam.interfaces.rest;

import com.platform.iam.application.command.*;
import com.platform.iam.application.query.*;
import com.platform.iam.application.service.UserApplicationService;
import com.platform.iam.domain.model.valueobjects.RoleId;
import com.platform.iam.domain.model.valueobjects.TenantId;
import com.platform.iam.domain.model.valueobjects.UserId;
import com.platform.iam.interfaces.rest.dto.ApiResponse;
import com.platform.iam.interfaces.rest.dto.ChangePasswordRequest;
import com.platform.iam.interfaces.rest.dto.CreateUserRequest;
import com.platform.iam.interfaces.rest.dto.ResetPasswordRequest;
import com.platform.iam.interfaces.rest.dto.UpdateUserInfoRequest;
import com.platform.iam.interfaces.rest.dto.UserDetailResponse;
import com.platform.iam.interfaces.rest.dto.UserListResponse;
import com.platform.iam.interfaces.rest.dto.UserStatisticsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户全生命周期管理 API")
public class UserController {

    private final UserApplicationService userService;
    private final RoleQueryService roleQueryService;

    @PostMapping
    @Operation(summary = "创建用户", description = "创建新用户(支持分配角色)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @RequestParam(value = "tenantId", required = false) String tenantIdParam,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        // 修复: 硬编码 "current-tenant" 导致 Invalid UUID 500; 现按 优先级取租户:
        // X-Tenant-Id 头 → tenantId 查询参数(空则 400)
        String tenantRaw = httpRequest.getHeader("X-Tenant-Id");
        if (tenantRaw == null || tenantRaw.isBlank()) tenantRaw = tenantIdParam;
        if (tenantRaw == null || tenantRaw.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("缺少租户: 请传 X-Tenant-Id 头或 tenantId 参数"));
        }
        TenantId tenantId = TenantId.of(tenantRaw);
        Set<RoleId> roleIds = request.roleCodes() == null ? Set.of() :
            request.roleCodes().stream()
                .map(code -> roleQueryService.findByCode(tenantId, code))
                .map(RoleDetailDto::id)
                .map(RoleId::of)
                .collect(Collectors.toSet());

        UserId userId = userService.createUser(new CreateUserCommand(
            tenantId, request.username(), request.email(),
            request.phone(), request.password(), roleIds));

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("用户创建成功", toResponse(userService.findById(userId))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询用户详情")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUser(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(toResponse(userService.findById(UserId.of(id)))));
    }

    @GetMapping
    @Operation(summary = "分页查询用户")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ApiResponse<PageResult<UserListResponse>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        PageResult<UserListDto> result = userService.findPage(
            new UserPageQuery(TenantId.of("current-tenant"), page, size, status, keyword, null, null));
        List<UserListResponse> content = result.content().stream().map(UserController::toListResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(new PageResult<>(
            content, result.page(), result.size(), result.totalElements(), result.totalPages(),
            result.first(), result.last())));
    }

    @GetMapping("/me")
    @Operation(summary = "查询当前登录用户")
    public ResponseEntity<ApiResponse<UserDetailResponse>> me(@RequestParam String username) {
        return ResponseEntity.ok(ApiResponse.success(
            toResponse(userService.findByUsername(TenantId.of("current-tenant"), username))));
    }

    @PutMapping("/{id}/info")
    @Operation(summary = "更新用户基本信息")
    public ResponseEntity<ApiResponse<Void>> updateInfo(@PathVariable String id,
                                                        @Valid @RequestBody UpdateUserInfoRequest request) {
        userService.updateUserInfo(new UpdateUserInfoCommand(
            UserId.of(id), request.email(), request.phone(), "current-user"));
        return ResponseEntity.ok(ApiResponse.success("用户信息更新成功", null));
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "修改密码")
    public ResponseEntity<ApiResponse<Void>> changePassword(@PathVariable String id,
                                                            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(new ChangePasswordCommand(
            UserId.of(id), request.oldPassword(), request.newPassword(), "current-user"));
        return ResponseEntity.ok(ApiResponse.success("密码修改成功", null));
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "重置密码(管理员)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable String id,
                                                           @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(new ResetPasswordCommand(UserId.of(id), request.newPassword(), "current-admin"));
        return ResponseEntity.ok(ApiResponse.success("密码重置成功", null));
    }

    @PostMapping("/{id}/assign-roles")
    @Operation(summary = "分配角色")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> assignRoles(@PathVariable String id,
                                                         @Valid @RequestBody com.platform.iam.interfaces.rest.dto.AssignRolesRequest request) {
        Set<RoleId> roleIds = request.roleCodes() == null ? Set.of() :
            request.roleCodes().stream()
                .map(code -> roleQueryService.findByCode(TenantId.of("current-tenant"), code))
                .map(RoleDetailDto::id)
                .map(RoleId::of)
                .collect(Collectors.toSet());
        userService.assignRoles(new AssignRolesCommand(UserId.of(id), roleIds, "current-admin"));
        return ResponseEntity.ok(ApiResponse.success("角色分配成功", null));
    }

    @PostMapping("/{id}/roles/{roleCode}/revoke")
    @Operation(summary = "撤销角色")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> revokeRole(@PathVariable String id, @PathVariable String roleCode) {
        RoleDetailDto role = roleQueryService.findByCode(TenantId.of("current-tenant"), roleCode);
        userService.revokeRole(new RevokeRoleCommand(UserId.of(id), RoleId.of(role.id()), "current-admin"));
        return ResponseEntity.ok(ApiResponse.success("角色撤销成功", null));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "激活用户")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable String id) {
        userService.activateUser(new ActivateUserCommand(UserId.of(id), "current-admin"));
        return ResponseEntity.ok(ApiResponse.success("用户已激活", null));
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "停用用户")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> suspend(@PathVariable String id, @RequestParam String reason) {
        userService.suspendUser(new SuspendUserCommand(UserId.of(id), reason, "current-admin"));
        return ResponseEntity.ok(ApiResponse.success("用户已停用", null));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "恢复用户")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> restore(@PathVariable String id) {
        userService.restoreUser(new RestoreUserCommand(UserId.of(id), "current-admin"));
        return ResponseEntity.ok(ApiResponse.success("用户已恢复", null));
    }

    @PostMapping("/{id}/lock")
    @Operation(summary = "锁定用户")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> lock(@PathVariable String id, @RequestParam String reason) {
        userService.lockUser(new LockUserCommand(UserId.of(id), reason, "current-admin"));
        return ResponseEntity.ok(ApiResponse.success("用户已锁定", null));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户(软删除)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id, @RequestParam String reason) {
        userService.deleteUser(new DeleteUserCommand(UserId.of(id), "current-admin", reason));
        return ResponseEntity.ok(ApiResponse.success("用户已删除", null));
    }

    @GetMapping("/statistics")
    @Operation(summary = "用户统计")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserStatisticsResponse>> statistics() {
        UserStatisticsDto s = userService.statistics(TenantId.of("current-tenant"));
        return ResponseEntity.ok(ApiResponse.success(new UserStatisticsResponse(
            s.total(), s.active(), s.pending(), s.suspended(), s.locked(), s.expired(), s.deleted())));
    }

    private static UserDetailResponse toResponse(UserDetailDto dto) {
        return new UserDetailResponse(dto.id(), dto.tenantId(), dto.username(), dto.email(),
            dto.phone(), dto.status(), dto.roleCodes(), dto.lastLoginAt(), dto.failedLoginAttempts(),
            dto.lockedAt(), dto.lockedReason(), dto.createdAt(), dto.updatedAt(),
            dto.deletedAt(), dto.deletedBy(), dto.deleteReason());
    }

    private static UserListResponse toListResponse(UserListDto dto) {
        return new UserListResponse(dto.id(), dto.tenantId(), dto.username(), dto.email(),
            dto.phone(), dto.status(), dto.lastLoginAt(), dto.failedLoginAttempts(), dto.createdAt());
    }
}
