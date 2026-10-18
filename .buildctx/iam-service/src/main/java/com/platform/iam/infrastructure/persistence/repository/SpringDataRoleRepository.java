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

    @Query(value="SELECT r FROM roles r WHERE r.tenantId = :tenantId " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(r.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:systemOnly = false OR r.isSystem = true) " +
           "ORDER BY r.createdAt DESC", nativeQuery=true)
    Page<RoleEntity> findWithFilters(@Param("tenantId") UUID tenantId,
                                      @Param("status") RoleStatus status,
                                      @Param("keyword") String keyword,
                                      @Param("systemOnly") boolean systemOnly,
                                      Pageable pageable);

    @Query(value="SELECT COUNT(r) FROM roles r WHERE r.tenantId = :tenantId " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(r.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:systemOnly = false OR r.isSystem = true)", nativeQuery=true)
    long countWithFilters(@Param("tenantId") UUID tenantId,
                           @Param("status") RoleStatus status,
                           @Param("keyword") String keyword,
                           @Param("systemOnly") boolean systemOnly);

    List<RoleEntity> findByUserId(UUID userId);

    List<RoleEntity> findByIds(List<UUID> ids);
}