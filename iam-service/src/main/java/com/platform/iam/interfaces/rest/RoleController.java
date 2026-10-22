package com.platform.iam.interfaces.rest;

import com.platform.iam.application.command.*;
import com.platform.iam.application.query.*;
import com.platform.iam.application.service.UserApplicationService;
import com.platform.iam.application.service.RoleApplicationService;
import com.platform.iam.domain.model.valueobjects.*;
import com.platform.iam.interfaces.rest.dto.*;
import com.platform.iam.interfaces.rest.assembler.RoleDtoAssembler;
import com.platform.iam.interfaces.rest.dto.PermissionDto;
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

/**
 * 角色管理控制器
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色全生命周期管理 API")
class RoleController {

    private final RoleApplicationService roleService;

    @PostMapping
    @Operation(summary = "创建角色", description = "创建新角色")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoleDetailResponse>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        Set<Permission> permissions = request.permissions() != null ?
            request.permissions().stream().map(PermissionDto::toDomain).collect(Collectors.toSet()) :
            Set.of();
        
        RoleId roleId = roleService.createRole(new CreateRoleCommand(
            TenantId.of(currentTenantId()), request.code(), request.name(),
            request.description(), request.isSystem(), permissions
        ));
        
        RoleDetailResponse response = RoleDtoAssembler.fromDetail(roleService.findById(roleId));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("角色创建成功", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取角色详情", description = "根据ID获取角色详细信息")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<RoleDetailResponse>> getRole(
            @Parameter(description = "角色ID") @PathVariable String id) {
        RoleDetailResponse response = RoleDtoAssembler.fromDetail(roleService.findById(RoleId.of(id)));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "分页查询角色列表", description = "分页查询角色列表")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<RoleListResponse>>> listRoles(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "关键词搜索") @RequestParam(required = false) String keyword,
            @Parameter(description = "仅系统角色") @RequestParam(defaultValue = "false") boolean systemOnly) {
        
        // 修复: 硬编码 "current-tenant" -> TenantId.of 抛 Invalid UUID 500(错误页 403)。
        // 租户取 JWT details(登录按用户所属租户签发), 兜底 X-Tenant-Id 头。
        String tenantRaw = com.platform.iam.infrastructure.security.SecurityContextUtil.getCurrentTenantId()
            .orElseGet(this::currentTenantHeader);
        RolePageQuery query = new RolePageQuery(
            TenantId.of(tenantRaw), page, size, status, keyword, systemOnly, "createdAt", "desc"
        );
        
        PageResponse<RoleListResponse> response = RoleDtoAssembler.fromPage(roleService.findPage(query));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/system")
    @Operation(summary = "获取系统内置角色", description = "获取所有系统内置角色")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<RoleListResponse>>> getSystemRoles() {
        java.util.List<RoleListResponse> response = RoleDtoAssembler.fromSimpleList(roleService.findSystemRoles());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}/info")
    @Operation(summary = "更新角色信息", description = "更新角色名称和描述")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoleDetailResponse>> updateRoleInfo(
            @Parameter(description = "角色ID") @PathVariable String id,
            @Valid @RequestBody UpdateRoleInfoRequest request) {
        roleService.updateRoleInfo(new UpdateRoleInfoCommand(RoleId.of(id), request.name(), request.description(), "current-user"));
        RoleDetailResponse response = RoleDtoAssembler.fromDetail(roleService.findById(RoleId.of(id)));
        return ResponseEntity.ok(ApiResponse.success("角色信息更新成功", response));
    }

    @PutMapping("/{id}/permissions")
    @Operation(summary = "设置角色权限", description = "全量设置角色权限")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoleDetailResponse>> setRolePermissions(
            @Parameter(description = "角色ID") @PathVariable String id,
            @Valid @RequestBody SetRolePermissionsRequest request) {
        Set<Permission> permissions = request.permissions().stream()
            .map(PermissionDto::toDomain).collect(Collectors.toSet());
        roleService.setRolePermissions(new SetRolePermissionsCommand(RoleId.of(id), permissions, "current-user"));
        RoleDetailResponse response = RoleDtoAssembler.fromDetail(roleService.findById(RoleId.of(id)));
        return ResponseEntity.ok(ApiResponse.success("角色权限设置成功", response));
    }

    @PostMapping("/{id}/permissions")
    @Operation(summary = "添加角色权限", description = "为角色添加单个权限")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> addRolePermission(
            @Parameter(description = "角色ID") @PathVariable String id,
            @Valid @RequestBody AddRolePermissionRequest request) {
        roleService.addRolePermission(new AddRolePermissionCommand(RoleId.of(id), request.permission().toDomain(), "current-user"));
        return ResponseEntity.ok(ApiResponse.success("权限添加成功", null));
    }

    @DeleteMapping("/{id}/permissions/{permissionCode}")
    @Operation(summary = "移除角色权限", description = "移除角色的指定权限")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeRolePermission(
            @Parameter(description = "角色ID") @PathVariable String id,
            @Parameter(description = "权限编码") @PathVariable String permissionCode) {
        roleService.removeRolePermission(new RemoveRolePermissionCommand(RoleId.of(id), Permission.fromCode(permissionCode), "current-user"));
        return ResponseEntity.ok(ApiResponse.success("权限移除成功", null));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "停用角色", description = "停用角色（非系统角色）")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoleDetailResponse>> deactivateRole(
            @Parameter(description = "角色ID") @PathVariable String id) {
        roleService.deactivateRole(new DeactivateRoleCommand(RoleId.of(id), "current-user"));
        RoleDetailResponse response = RoleDtoAssembler.fromDetail(roleService.findById(RoleId.of(id)));
        return ResponseEntity.ok(ApiResponse.success("角色停用成功", response));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "激活角色", description = "激活已停用的角色")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoleDetailResponse>> activateRole(
            @Parameter(description = "角色ID") @PathVariable String id) {
        roleService.activateRole(new ActivateRoleCommand(RoleId.of(id), "current-user"));
        RoleDetailResponse response = RoleDtoAssembler.fromDetail(roleService.findById(RoleId.of(id)));
        return ResponseEntity.ok(ApiResponse.success("角色激活成功", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色", description = "删除角色（非系统角色）")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @Parameter(description = "角色ID") @PathVariable String id,
            @Valid @RequestBody DeleteRoleRequest request) {
        roleService.deleteRole(new DeleteRoleCommand(RoleId.of(id), "current-user", request.reason()));
        return ResponseEntity.ok(ApiResponse.success("角色删除成功", null));
    }

    /**
     * 修复: 硬编码 "current-tenant" 使 TenantId.of 抛 Invalid UUID(500→错误页403)。
     * 租户优先取 JWT details, 兜底 X-Tenant-Id 请求头。
     */
    private String currentTenantId() {
        return com.platform.iam.infrastructure.security.SecurityContextUtil.getCurrentTenantId()
            .orElseGet(this::currentTenantHeader);
    }

    private String currentTenantHeader() {
        var attr = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attr instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
            String h = sra.getRequest().getHeader("X-Tenant-Id");
            if (h != null && !h.isBlank()) return h;
        }
        throw new IllegalArgumentException("缺少租户: JWT 无 tenant_id 且未传 X-Tenant-Id 头");
    }
}
