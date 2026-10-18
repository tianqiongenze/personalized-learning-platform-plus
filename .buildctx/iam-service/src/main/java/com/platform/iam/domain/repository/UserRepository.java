package com.platform.iam.domain.repository;

import com.platform.iam.domain.model.User;
import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓储接口
 */

/**
 * 权限仓储接口
 */

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UserId id);
    Optional<User> findByUsername(TenantId tenantId, String username);
    Optional<User> findByEmail(TenantId tenantId, String email);
    Optional<User> findByPhone(TenantId tenantId, String phone);
    boolean existsByUsername(TenantId tenantId, String username);
    boolean existsByEmail(TenantId tenantId, String email);
    boolean existsByPhone(TenantId tenantId, String phone);
    List<User> findByTenantId(TenantId tenantId, int page, int size, String status, String keyword);
    long countByTenantId(TenantId tenantId, String status, String keyword);
    List<User> findByRoleId(RoleId roleId);
    List<User> findByIds(List<UserId> ids);
    void deleteById(UserId id);
}
