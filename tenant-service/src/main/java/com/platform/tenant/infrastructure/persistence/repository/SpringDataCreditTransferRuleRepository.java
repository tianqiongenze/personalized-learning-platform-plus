package com.platform.tenant.infrastructure.persistence.repository;

import com.platform.tenant.infrastructure.persistence.entity.CreditTransferRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA 学分互认规则仓储
 */
@Repository
public interface SpringDataCreditTransferRuleRepository
        extends JpaRepository<CreditTransferRuleEntity, UUID> {

    @Query(value = "SELECT r.* FROM credit_transfer_rules r WHERE r.source_tenant_id = :tenantId", nativeQuery = true)
    List<CreditTransferRuleEntity> findBySourceTenantId(@Param("tenantId") UUID tenantId);

    @Query(value = "SELECT r.* FROM credit_transfer_rules r WHERE r.target_tenant_id = :tenantId", nativeQuery = true)
    List<CreditTransferRuleEntity> findByTargetTenantId(@Param("tenantId") UUID tenantId);

    @Query(value = "SELECT r.* FROM credit_transfer_rules r " +
           "WHERE r.source_tenant_id = :tenantId AND r.source_course_code = :courseCode " +
           "AND r.status = 'ACTIVE' LIMIT 1", nativeQuery = true)
    Optional<CreditTransferRuleEntity> findActiveBySource(@Param("tenantId") UUID tenantId,
                                                          @Param("courseCode") String courseCode);
}
