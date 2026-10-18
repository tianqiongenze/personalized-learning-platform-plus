package com.platform.tenant.domain.repository;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.util.List;
import java.util.Optional;

/**
 * 租户仓储接口
 * 定义租户聚合根的持久化操作契约
 */
public interface TenantRepository {
    /**
     * 保存租户（新增或更新）
     */
    Tenant save(Tenant tenant);

    /**
     * 根据ID查找租户
     */
    Optional<Tenant> findById(TenantId id);

    /**
     * 根据编码查找租户
     */
    Optional<Tenant> findByCode(String code);

    /**
     * 根据管理员用户ID查找租户
     */
    Optional<Tenant> findByAdminUserId(String adminUserId);

    /**
     * 检查编码是否存在
     */
    boolean existsByCode(String code);

    /**
     * 检查编码是否存在（排除指定ID）
     */
    boolean existsByCodeAndIdNot(String code, TenantId excludeId);

    /**
     * 分页查询租户列表
     */
    List<Tenant> findAll(int page, int size, String status, String keyword);

    /**
     * 统计租户总数
     */
    long count(String status, String keyword);

    /**
     * 统计指定状态的租户数量
     */
    long countByStatus(String status);

    /**
     * 删除租户（物理删除，慎用）
     */
    void deleteById(TenantId id);

    /**
     * 批量查找
     */
    List<Tenant> findByIds(List<TenantId> ids);
}