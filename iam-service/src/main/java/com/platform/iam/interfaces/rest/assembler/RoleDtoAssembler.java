package com.platform.iam.interfaces.rest.assembler;

import com.platform.iam.application.query.PageResult;
import com.platform.iam.application.query.RoleDetailDto;
import com.platform.iam.application.query.RoleListDto;
import com.platform.iam.application.query.RoleSimpleDto;
import com.platform.iam.interfaces.rest.dto.PageResponse;
import com.platform.iam.interfaces.rest.dto.PermissionDto;
import com.platform.iam.interfaces.rest.dto.RoleDetailResponse;
import com.platform.iam.interfaces.rest.dto.RoleListResponse;

import java.util.List;

/**
 * 角色 DTO 装配器: 应用层查询 DTO -> REST 响应
 */
public final class RoleDtoAssembler {

    private RoleDtoAssembler() {}

    public static RoleDetailResponse fromDetail(RoleDetailDto dto) {
        return new RoleDetailResponse(
            dto.id(), dto.tenantId(), dto.code(), dto.name(), dto.description(),
            dto.status(), dto.isSystem(),
            dto.permissions() == null ? java.util.Collections.<PermissionDto>emptySet() : dto.permissions().stream()
                .map(p -> new PermissionDto(p.resourceType(), p.action(), p.resourceId()))
                .collect(java.util.stream.Collectors.toSet()),
            dto.createdAt(), dto.updatedAt(), dto.deletedAt(), dto.deletedBy(), dto.deleteReason());
    }

    public static RoleListResponse fromList(RoleListDto dto) {
        return new RoleListResponse(
            dto.id(), dto.tenantId(), dto.code(), dto.name(), dto.description(),
            dto.status(), dto.isSystem(), dto.permissionCount(), dto.createdAt());
    }

    public static PageResponse<RoleListResponse> fromPage(PageResult<RoleListDto> page) {
        return new PageResponse<>(
            page.content().stream().map(RoleDtoAssembler::fromList).toList(),
            page.page(), page.size(), page.totalElements(), page.totalPages(),
            page.first(), page.last());
    }

    public static List<RoleListResponse> fromSimpleList(List<RoleSimpleDto> list) {
        return list == null ? List.of() : list.stream()
            .map(dto -> new RoleListResponse(dto.id(), null, dto.code(), dto.name(), null,
                null, false, 0, null))
            .toList();
    }
}
