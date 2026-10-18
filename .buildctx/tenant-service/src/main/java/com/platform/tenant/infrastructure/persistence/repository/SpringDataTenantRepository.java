package com.platform.tenant.infrastructure.persistence.repository;

import com.platform.tenant.domain.model.valueobjects.TenantId;
import com.platform.tenant.domain.model.valueobjects.TenantStatus;
import com.platform.tenant.infrastructure.persistence.entity.TenantEntity;
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
 * Spring Data JPA 租户仓储
 */
@Repository
public interface SpringDataTenantRepository extends JpaRepository<TenantEntity, UUID> {

    @Query(value="SELECT t FROM tenants t WHERE t.code = :code", nativeQuery=true)
    Optional<TenantEntity> findByCode(@Param("code") String code);

    @Query(value="SELECT t FROM tenants t WHERE t.adminUserId = :adminUserId", nativeQuery=true)
    Optional<TenantEntity> findByAdminUserId(@Param("adminUserId") String adminUserId);

    @Query(value="SELECT CASE WHEN COUNT(t)>0 THEN TRUE ELSE FALSE END FROM tenants t WHERE t.code = :code", nativeQuery=true)
    boolean existsByCode(@Param("code") String code);

    @org.springframework.data.jpa.repository.Query(value="SELECT CASE WHEN COUNT(t) > 0 THEN TRUE ELSE FALSE END FROM tenants t WHERE t.code = :code AND t.id <> :id", nativeQuery=true)
    boolean existsByCodeAndIdNot(@org.springframework.data.repository.query.Param("code") String code, @org.springframework.data.repository.query.Param("id") UUID id);

    @Query(value="SELECT t FROM tenants t WHERE t.status = :status", nativeQuery=true)
    List<TenantEntity> findByStatus(@Param("status") TenantStatus status);

    @Query(value="SELECT COUNT(t) FROM tenants t WHERE t.status = :status", nativeQuery=true)
    long countByStatus(@Param("status") TenantStatus status);

    @Query(value="SELECT COUNT(t) FROM tenants t WHERE (:status IS NULL OR t.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(t.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')))", nativeQuery=true)
    long countWithFilters(@Param("status") TenantStatus status, @Param("keyword") String keyword);

    @Query(value="SELECT t FROM tenants t WHERE (:status IS NULL OR t.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(t.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY t.createdAt DESC", nativeQuery=true)
    Page<TenantEntity> findWithFilters(@Param("status") TenantStatus status, 
                                        @Param("keyword") String keyword, 
                                        Pageable pageable);

    @Query(value="SELECT t FROM tenants t WHERE t.status = :status AND t.code LIKE CONCAT(:prefix, '%')", nativeQuery=true)
    List<TenantEntity> findByStatusAndCodeStartingWith(@Param("status") TenantStatus status, @Param("prefix") String prefix);

    @Query(value="SELECT t FROM tenants t WHERE t.status <> 'DELETED' ORDER BY t.createdAt DESC", nativeQuery=true)
    List<TenantEntity> findAllActiveOrdered();
}