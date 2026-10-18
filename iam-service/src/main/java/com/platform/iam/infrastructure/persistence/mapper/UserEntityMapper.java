package com.platform.iam.infrastructure.persistence.mapper;

import com.platform.iam.domain.model.User;
import com.platform.iam.domain.model.valueobjects.RoleId;
import com.platform.iam.domain.model.valueobjects.UserId;
import com.platform.iam.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户实体映射器(手工实现: User 领域模型构造器受限, MapStruct 难以直接映射)
 */
@Component
public class UserEntityMapper {

    public UserEntity toEntity(User user) {
        UserEntity e = new UserEntity();
        e.setId(user.getId() != null ? user.getId().getValue() : null);
        e.setTenantId(user.getTenantId() != null ? user.getTenantId().getValue() : null);
        e.setUsername(user.getUsername());
        e.setEmail(user.getEmail());
        e.setPhone(user.getPhone());
        e.setPasswordHash(user.getPasswordHash());
        e.setStatus(user.getStatus());
        e.setVersion(e.getVersion()); // merge 需要: 保留 DB 现值由 em.merge 以入参 version 做乐观锁比对
        // 注: 新实体 version 为 null, 由 Hibernate 首次 persist 赋值
        e.setRoleIds(roleIdsToArray(user.getRoleIds()));
        return e;
    }

    public User toDomain(UserEntity e) {
        // 通过领域模型工厂重建(默认 ACTIVE, 状态由仓储实现按需覆盖)
        Set<RoleId> roleIds = arrayToRoleIds(e.getRoleIds());
        return User.reconstruct(
            UserId.of(e.getId()), com.platform.iam.domain.model.valueobjects.TenantId.of(e.getTenantId()),
            e.getUsername(), e.getEmail(), e.getPhone(), e.getPasswordHash(),
            e.getStatus() == null ? com.platform.iam.domain.model.valueobjects.UserStatus.ACTIVE : e.getStatus(), roleIds,
            e.getLastLoginAt(), e.getFailedLoginAttempts(), e.getLockedAt(), e.getLockedReason(),
            e.getCreatedAt(), e.getUpdatedAt(), e.getDeletedAt(), e.getDeletedBy(), e.getDeleteReason());
    }

    @Named("roleIdsToArray")
    public static UUID[] roleIdsToArray(Set<RoleId> roleIds) {
        return roleIds != null ?
            roleIds.stream().map(RoleId::getValue).toArray(UUID[]::new) :
            new UUID[0];
    }

    @Named("arrayToRoleIds")
    public static Set<RoleId> arrayToRoleIds(UUID[] roleIds) {
        return roleIds != null ?
            java.util.Arrays.stream(roleIds).map(RoleId::of).collect(Collectors.toSet()) :
            Set.of();
    }
}
