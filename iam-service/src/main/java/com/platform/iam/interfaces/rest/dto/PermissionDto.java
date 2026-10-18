package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record PermissionDto(
    @NotNull String resourceType,
    @NotNull String action,
    String resourceId
) {
    public Permission toDomain() {
        return Permission.of(
            Permission.ResourceType.valueOf(resourceType.toUpperCase()),
            Permission.Action.valueOf(action.toUpperCase()),
            resourceId
        );
    }
}
