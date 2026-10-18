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

    @Query(value="SELECT u FROM users u WHERE u.tenantId = :tenantId " +
           "AND (:status IS NULL OR u.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY u.createdAt DESC", nativeQuery=true)
    Page<UserEntity> findWithFilters(@Param("tenantId") UUID tenantId,
                                      @Param("status") UserStatus status,
                                      @Param("keyword") String keyword,
                                      Pageable pageable);

    @Query(value="SELECT COUNT(u) FROM users u WHERE u.tenantId = :tenantId " +
           "AND (:status IS NULL OR u.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))", nativeQuery=true)
    long countWithFilters(@Param("tenantId") UUID tenantId,
                           @Param("status") UserStatus status,
                           @Param("keyword") String keyword);

    @Query(value = "SELECT * FROM users WHERE :roleId = ANY(role_ids)", nativeQuery = true)
    List<UserEntity> findByRoleIdsContaining(@Param("roleId") UUID roleId);

@Query(value="SELECT u FROM users u WHERE u.id IN :ids", nativeQuery=true)
    List<UserEntity> findByIds(@Param("ids") List<UUID> ids);


    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, UserStatus status);
}