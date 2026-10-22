package com.platform.iam.infrastructure.persistence.repository;

import com.platform.iam.domain.model.valueobjects.*;
import com.platform.iam.domain.model.valueobjects.RoleStatus;
import com.platform.iam.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA 角色仓储
 */
@Repository
public interface SpringDataRoleRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    List<RoleEntity> findByTenantIdAndStatus(UUID tenantId, RoleStatus status);

    List<RoleEntity> findByIsSystemTrue();

    // 修复: 原写 JPQL 语法却标 nativeQuery=true, TiDB 把 r.tenantId/r.createdAt
    // 当字面驼峰列报 Unknown column。改真实原生 SQL(下划线列名)。
    // 可选过滤(:status/:keyword null 二元绑定)沿用 course D-04 v2 教训: TiDB 对
    // null 绑定按 binary 处理会撞 utf8mb4 —— 移除可选参数, 由调用方按空值分支。
    @Query(value="SELECT r.* FROM roles r WHERE r.tenant_id = :tenantId AND r.status = :status " +
           "AND (LOWER(r.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY r.created_at DESC",
           countQuery="SELECT COUNT(*) FROM roles r WHERE r.tenant_id = :tenantId AND r.status = :status " +
           "AND (LOWER(r.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')))", nativeQuery=true)
    Page<RoleEntity> findWithFiltersKeyword(@Param("tenantId") String tenantId,
                                            @Param("status") String status,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);

    @Query(value="SELECT r.* FROM roles r WHERE r.tenant_id = :tenantId AND r.status = :status " +
           "ORDER BY r.created_at DESC",
           countQuery="SELECT COUNT(*) FROM roles r WHERE r.tenant_id = :tenantId AND r.status = :status", nativeQuery=true)
    Page<RoleEntity> findWithFiltersNoKeyword(@Param("tenantId") String tenantId,
                                              @Param("status") String status,
                                              Pageable pageable);

    @Query(value="SELECT COUNT(*) FROM roles r WHERE r.tenant_id = :tenantId AND r.status = :status " +
           "AND (LOWER(r.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')))", nativeQuery=true)
    long countWithFiltersKeyword(@Param("tenantId") String tenantId,
                                 @Param("status") String status,
                                 @Param("keyword") String keyword);

    @Query(value="SELECT COUNT(*) FROM roles r WHERE r.tenant_id = :tenantId AND r.status = :status", nativeQuery=true)
    long countWithFiltersNoKeyword(@Param("tenantId") String tenantId,
                                   @Param("status") String status);


    // D-03 修复: 原 native SQL "unnest(u.role_ids)" 为 PG 专有函数, TiDB 报
    // FUNCTION iam_db.unnest does not exist 导致登录 403。
    // role_ids 为 JSON 数组列(实体侧 UuidArrayJsonConverter 已解析为 UUID[]),
    // 拆为两步: 先取用户实体读 roleIds, 再按 id 列表查角色 —— 全 ANSI, PG/CRDB/TiDB 三存储兼容。
    // 见 RoleRepositoryImpl.findByUserId。

    // D-03 修复(v2): native IN 里传 List<UUID> 时 Hibernate 绑定为 VARBINARY,
    // 与 char(36) 列比较在 TiDB 下不匹配静默返空(角色丢失)。改绑 String。
    @Query(value="SELECT r.* FROM roles r WHERE r.id IN (:ids)", nativeQuery=true)
    List<RoleEntity> findByIds(@Param("ids") List<String> ids);

    /**
     * D-03 修复: 原 native SQL unnest(u.role_ids) 为 PG 专有, TiDB 不支持致登录 403。
     * 改为 ANSI 两步: 先读用户的 role_ids(JSON 列, 实体转换器已解析为 UUID[]), 再按 id 查角色。
     * IamQueryServiceImpl.findByUserId 直接经此入口取角色(全 ANSI, PG/CRDB/TiDB 三存储兼容)。
     * 见 RoleRepositoryImpl.findByUserId。
     */
    default List<RoleEntity> findByUserId(UUID userId,
            com.platform.iam.infrastructure.persistence.repository.SpringDataUserRepository userRepo) {
        var user = userRepo.findById(userId).orElse(null);
        if (user == null || user.getRoleIds() == null || user.getRoleIds().length == 0) {
            return List.of();
        }
        return findByIds(java.util.Arrays.stream(user.getRoleIds())
            .map(UUID::toString).collect(java.util.stream.Collectors.toList()));
    }

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, RoleStatus status);

    long countByTenantIdAndIsSystem(UUID tenantId, boolean isSystem);
}