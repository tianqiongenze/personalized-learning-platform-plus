package com.platform.tenant.infrastructure.persistence.repository;

import com.platform.tenant.infrastructure.persistence.entity.CreditTransferRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA 学分互认记录仓储
 */
@Repository
public interface SpringDataCreditTransferRecordRepository
        extends JpaRepository<CreditTransferRecordEntity, UUID> {
}
