package com.platform.iam.domain.repository;

import com.platform.iam.domain.model.User;
import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓储接口
 */

public interface RoleRepository {
    Role save(Role role);
    Optional<Role> findById(RoleId id);
    Optional<Role> findByCode(TenantId tenantId, String code);
    boolean existsByCode(TenantId tenantId, String code);
    List<Role> findByTenantId(TenantId tenantId, int page, int size, String status, String keyword);
    long countByTenantId(TenantId tenantId, String status, String keyword);
    List<Role> findByUserId(UserId userId);
    List<Role> findSystemRoles();
    List<Role> findByIds(List<RoleId> ids);
    void deleteById(RoleId id);
}
