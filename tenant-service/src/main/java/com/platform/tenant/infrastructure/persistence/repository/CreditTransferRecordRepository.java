package com.platform.tenant.infrastructure.persistence.repository;

import com.platform.tenant.domain.repository.CreditTransferRecord;
import org.springframework.stereotype.Repository;

/**
 * 学分互认记录仓储（JPA 写入口）
 */
@Repository
public class CreditTransferRecordRepository {

    private final SpringDataCreditTransferRecordRepository jpaRepository;

    public CreditTransferRecordRepository(SpringDataCreditTransferRecordRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    public CreditTransferRecord save(CreditTransferRecord record) {
        var entity = toEntity(record);
        var saved = jpaRepository.save(entity);
        return new CreditTransferRecord(
            saved.getId().toString(),
            saved.getStudentId().toString(),
            saved.getRuleId().toString(),
            saved.getSourceScore(),
            saved.getGrantedCredits(),
            saved.getAppliedAt()
        );
    }

    private com.platform.tenant.infrastructure.persistence.entity.CreditTransferRecordEntity toEntity(
            CreditTransferRecord record) {
        return com.platform.tenant.infrastructure.persistence.entity.CreditTransferRecordEntity.builder()
            .id(java.util.UUID.fromString(record.id()))
            .studentId(java.util.UUID.fromString(record.studentId()))
            .ruleId(java.util.UUID.fromString(record.ruleId()))
            .sourceScore(record.sourceScore())
            .grantedCredits(record.grantedCredits())
            .appliedAt(record.appliedAt())
            .build();
    }
}
