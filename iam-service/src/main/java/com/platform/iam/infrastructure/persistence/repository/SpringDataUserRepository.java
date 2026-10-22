package com.platform.iam.infrastructure.persistence.repository;

import com.platform.iam.domain.model.valueobjects.*;
import com.platform.iam.domain.model.valueobjects.UserStatus;
import com.platform.iam.domain.model.valueobjects.RoleStatus;
import com.platform.iam.infrastructure.persistence.entity.UserEntity;
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
 * Spring Data JPA 用户仓储
 */
@Repository
public interface SpringDataUserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByTenantIdAndUsername(UUID tenantId, String username);

    Optional<UserEntity> findByTenantIdAndEmail(UUID tenantId, String email);

    Optional<UserEntity> findByTenantIdAndPhone(UUID tenantId, String phone);

    boolean existsByTenantIdAndUsername(UUID tenantId, String username);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantIdAndPhone(UUID tenantId, String phone);

    List<UserEntity> findByTenantIdAndStatus(UUID tenantId, UserStatus status);

    // D-09 修复: 原 JPQL 语法标 nativeQuery=true -> TiDB 报 Unknown column 'u.createdAt'
    // (错误页 403)。改真原生 SQL; 可选过滤按 D-04 v2 拆 keyword 分支; UUID/status
    // 传 String 避免 VARBINARY/ordinal 绑定陷阱; 显式 countQuery 防 Spring 生成
    // TiDB 不支持的 count(u.*)。
    @Query(value="SELECT u.* FROM users u WHERE u.tenant_id = :tenantId AND u.status = :status " +
           "AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY u.created_at DESC",
           countQuery="SELECT COUNT(*) FROM users u WHERE u.tenant_id = :tenantId AND u.status = :status " +
           "AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))", nativeQuery=true)
    Page<UserEntity> findWithFiltersKeyword(@Param("tenantId") String tenantId,
                                            @Param("status") String status,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);

    @Query(value="SELECT u.* FROM users u WHERE u.tenant_id = :tenantId AND u.status = :status " +
           "ORDER BY u.created_at DESC",
           countQuery="SELECT COUNT(*) FROM users u WHERE u.tenant_id = :tenantId AND u.status = :status", nativeQuery=true)
    Page<UserEntity> findWithFiltersNoKeyword(@Param("tenantId") String tenantId,
                                              @Param("status") String status,
                                              Pageable pageable);

    @Query(value="SELECT COUNT(*) FROM users u WHERE u.tenant_id = :tenantId AND u.status = :status " +
           "AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))", nativeQuery=true)
    long countWithFiltersKeyword(@Param("tenantId") String tenantId,
                                 @Param("status") String status,
                                 @Param("keyword") String keyword);

    @Query(value="SELECT COUNT(*) FROM users u WHERE u.tenant_id = :tenantId AND u.status = :status", nativeQuery=true)
    long countWithFiltersNoKeyword(@Param("tenantId") String tenantId,
                                   @Param("status") String status);

    @Query(value = "SELECT * FROM users WHERE :roleId = ANY(role_ids)", nativeQuery = true)
    List<UserEntity> findByRoleIdsContaining(@Param("roleId") UUID roleId);

@Query(value="SELECT u FROM users u WHERE u.id IN :ids", nativeQuery=true)
    List<UserEntity> findByIds(@Param("ids") List<UUID> ids);


    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, UserStatus status);
}