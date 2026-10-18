package com.platform.tenant.interfaces.rest;

import com.platform.tenant.application.command.*;
import com.platform.tenant.application.query.*;
import com.platform.tenant.application.service.TenantApplicationService;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import com.platform.tenant.interfaces.rest.assembler.TenantDtoAssembler;
import com.platform.tenant.interfaces.rest.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户管理控制器
 * 提供租户全生命周期管理的 REST API
 */
@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
@Tag(name = "租户管理", description = "租户全生命周期管理 API")
public class TenantController {

    private final TenantApplicationService tenantService;
    private final TenantDtoAssembler dtoAssembler;

    // ========== 创建租户 ==========

    @PostMapping
    @Operation(summary = "创建租户", description = "创建新租户，仅超级管理员可访问")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> createTenant(
            @Valid @RequestBody TenantCreateRequest request) {
        TenantId tenantId = tenantService.createTenant(dtoAssembler.toCommand(request));
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findById(tenantId)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("租户创建成功", response));
    }

    // ========== 查询租户 ==========

    @GetMapping("/{id}")
    @Operation(summary = "获取租户详情", description = "根据ID获取租户详细信息")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> getTenant(
            @Parameter(description = "租户ID") @PathVariable String id) {
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findById(TenantId.of(id))
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "根据编码获取租户", description = "根据租户编码获取租户详细信息")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> getTenantByCode(
            @Parameter(description = "租户编码") @PathVariable String code) {
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findByCode(code)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "分页查询租户列表", description = "分页查询租户列表，支持状态筛选和关键词搜索")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<TenantListResponse>>> listTenants(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "关键词搜索") @RequestParam(required = false) String keyword,
            @Parameter(description = "排序字段") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "排序方向") @RequestParam(defaultValue = "desc") String sortOrder) {
        
        TenantPageRequest request = new TenantPageRequest(page, size, status, keyword, sortBy, sortOrder);
        TenantPageQuery query = new TenantPageQuery(
            request.page(), request.size(), request.status(), request.keyword(),
            request.sortBy(), request.sortOrder()
        );
        
        PageResponse<TenantListResponse> response = dtoAssembler.toPageResponse(
            tenantService.findPage(query)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/active")
    @Operation(summary = "获取所有激活态租户", description = "获取所有激活态租户，用于下拉选择")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<List<TenantSimpleResponse>>> getActiveTenants() {
        List<TenantSimpleResponse> response = tenantService.findAllActive().stream()
            .map(dtoAssembler::toSimpleResponse)
            .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/statistics")
    @Operation(summary = "获取租户统计信息", description = "获取各状态租户数量统计")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TenantStatisticsResponse>> getStatistics() {
        TenantStatisticsResponse response = dtoAssembler.toStatisticsResponse(
            tenantService.statistics()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== 更新租户 ==========

    @PutMapping("/{id}/info")
    @Operation(summary = "更新租户基本信息", description = "更新租户名称和描述")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> updateTenantInfo(
            @Parameter(description = "租户ID") @PathVariable String id,
            @Valid @RequestBody TenantUpdateInfoRequest request) {
        tenantService.updateTenantInfo(new UpdateTenantInfoCommand(
            TenantId.of(id), request.name(), request.description(), getCurrentUserId()
        ));
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findById(TenantId.of(id))
        );
        return ResponseEntity.ok(ApiResponse.success("租户信息更新成功", response));
    }

    @PutMapping("/{id}/code")
    @Operation(summary = "更新租户编码", description = "更新租户编码，仅草稿态允许")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> updateTenantCode(
            @Parameter(description = "租户ID") @PathVariable String id,
            @Valid @RequestBody TenantUpdateCodeRequest request) {
        tenantService.updateTenantCode(new UpdateTenantCodeCommand(
            TenantId.of(id), request.code(), getCurrentUserId()
        ));
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findById(TenantId.of(id))
        );
        return ResponseEntity.ok(ApiResponse.success("租户编码更新成功", response));
    }

    @PutMapping("/{id}/quota")
    @Operation(summary = "更新租户配额", description = "更新租户资源配额")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> updateTenantQuota(
            @Parameter(description = "租户ID") @PathVariable String id,
            @Valid @RequestBody TenantUpdateQuotaRequest request) {
        tenantService.updateTenantQuota(new UpdateTenantQuotaCommand(
            TenantId.of(id), request.quota(), getCurrentUserId()
        ));
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findById(TenantId.of(id))
        );
        return ResponseEntity.ok(ApiResponse.success("租户配额更新成功", response));
    }

    @PutMapping("/{id}/branding")
    @Operation(summary = "更新租户品牌定制", description = "更新租户品牌定制配置")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> updateTenantBranding(
            @Parameter(description = "租户ID") @PathVariable String id,
            @Valid @RequestBody TenantUpdateBrandingRequest request) {
        tenantService.updateTenantBranding(new UpdateTenantBrandingCommand(
            TenantId.of(id), request.branding(), getCurrentUserId()
        ));
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findById(TenantId.of(id))
        );
        return ResponseEntity.ok(ApiResponse.success("品牌定制更新成功", response));
    }

    @PutMapping("/{id}/config")
    @Operation(summary = "更新租户配置", description = "更新租户功能开关、集成配置等")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> updateTenantConfig(
            @Parameter(description = "租户ID") @PathVariable String id,
            @Valid @RequestBody TenantUpdateConfigRequest request) {
        tenantService.updateTenantConfig(new UpdateTenantConfigCommand(
            TenantId.of(id), request.config(), getCurrentUserId()
        ));
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findById(TenantId.of(id))
        );
        return ResponseEntity.ok(ApiResponse.success("租户配置更新成功", response));
    }

    @PutMapping("/{id}/admin")
    @Operation(summary = "更新租户管理员", description = "更新租户管理员用户ID")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> updateTenantAdmin(
            @Parameter(description = "租户ID") @PathVariable String id,
            @Valid @RequestBody TenantUpdateAdminRequest request) {
        tenantService.updateTenantAdmin(new UpdateTenantAdminCommand(
            TenantId.of(id), request.adminUserId(), getCurrentUserId()
        ));
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findById(TenantId.of(id))
        );
        return ResponseEntity.ok(ApiResponse.success("管理员更新成功", response));
    }

    // ========== 状态流转 ==========

    @PostMapping("/{id}/activate")
    @Operation(summary = "激活租户", description = "将草稿态租户激活为正常使用态")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> activateTenant(
            @Parameter(description = "租户ID") @PathVariable String id) {
        tenantService.activateTenant(new ActivateTenantCommand(TenantId.of(id), getCurrentUserId()));
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findById(TenantId.of(id))
        );
        return ResponseEntity.ok(ApiResponse.success("租户激活成功", response));
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "暂停租户", description = "暂停租户使用，数据保留")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> suspendTenant(
            @Parameter(description = "租户ID") @PathVariable String id,
            @Valid @RequestBody TenantSuspendRequest request) {
        tenantService.suspendTenant(new SuspendTenantCommand(
            TenantId.of(id), request.reason(), getCurrentUserId()
        ));
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findById(TenantId.of(id))
        );
        return ResponseEntity.ok(ApiResponse.success("租户暂停成功", response));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "恢复租户", description = "恢复暂停/过期/锁定/删除态的租户")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> restoreTenant(
            @Parameter(description = "租户ID") @PathVariable String id) {
        tenantService.restoreTenant(new RestoreTenantCommand(TenantId.of(id), getCurrentUserId()));
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findById(TenantId.of(id))
        );
        return ResponseEntity.ok(ApiResponse.success("租户恢复成功", response));
    }

    @PostMapping("/{id}/expire")
    @Operation(summary = "过期租户", description = "将租户标记为过期态")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> expireTenant(
            @Parameter(description = "租户ID") @PathVariable String id) {
        tenantService.expireTenant(new ExpireTenantCommand(TenantId.of(id), getCurrentUserId()));
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findById(TenantId.of(id))
        );
        return ResponseEntity.ok(ApiResponse.success("租户过期成功", response));
    }

    @PostMapping("/{id}/lock")
    @Operation(summary = "锁定租户", description = "锁定租户，需人工审核解锁")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> lockTenant(
            @Parameter(description = "租户ID") @PathVariable String id,
            @Valid @RequestBody TenantLockRequest request) {
        tenantService.lockTenant(new LockTenantCommand(
            TenantId.of(id), request.reason(), getCurrentUserId()
        ));
        TenantDetailResponse response = dtoAssembler.toDetailResponse(
            tenantService.findById(TenantId.of(id))
        );
        return ResponseEntity.ok(ApiResponse.success("租户锁定成功", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除租户", description = "软删除租户，进入回收站")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTenant(
            @Parameter(description = "租户ID") @PathVariable String id,
            @Valid @RequestBody TenantDeleteRequest request) {
        tenantService.deleteTenant(new DeleteTenantCommand(
            TenantId.of(id), getCurrentUserId(), request.reason()
        ));
        return ResponseEntity.ok(ApiResponse.success("租户删除成功", null));
    }

    private String getCurrentUserId() {
        // 实际应从 SecurityContext 获取
        return "system";
    }
}