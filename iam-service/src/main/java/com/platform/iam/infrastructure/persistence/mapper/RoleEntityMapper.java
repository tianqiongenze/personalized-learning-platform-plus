package com.platform.iam.infrastructure.persistence.mapper;

import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import com.platform.iam.infrastructure.persistence.entity.RoleEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色实体映射器(手工实现; permissions 以 JSONB 存取)
 */
@Component
public class RoleEntityMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public RoleEntity toEntity(Role role) {
        RoleEntity e = new RoleEntity();
        e.setId(role.getId() != null ? role.getId().getValue() : null);
        e.setTenantIdValue(role.getTenantId());
        e.setCode(role.getCode());
        e.setName(role.getName());
        e.setDescription(role.getDescription());
        e.setStatus(role.getStatus() == null ? RoleStatus.ACTIVE : role.getStatus());
        e.setSystem(role.isSystem());
        e.setPermissionSet(role.getPermissions());
        return e;
    }

    public Role toDomain(RoleEntity e) {
        return Role.reconstruct(
            RoleId.of(e.getId()), e.getTenantIdValue(), e.getCode(), e.getName(), e.getDescription(),
            e.getPermissionSet(), e.getStatus() == null ? RoleStatus.ACTIVE : e.getStatus(), e.isSystem(),
            e.getCreatedAt(), e.getUpdatedAt(), e.getDeletedAt(), e.getDeletedBy(), e.getDeleteReason());
    }

    private static RoleStatus parseStatus(String status) {
        try {
            return status == null ? RoleStatus.ACTIVE : RoleStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            return RoleStatus.ACTIVE;
        }
    }
}
