package com.platform.iam.domain.repository;

import com.platform.iam.domain.model.User;
import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓储接口
 */
public interface PermissionRepository {
    List<Permission> findByRoleId(RoleId roleId);
    List<Permission> findByUserId(UserId userId);
    List<Permission> findByTenantId(TenantId tenantId);
    boolean existsByCode(String code);
}
